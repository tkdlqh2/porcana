package com.porcana.domain.portfolio.service;

import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.dto.personality.AssetPersonality;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.service.personality.AssetPersonalityRuleEngine;
import com.porcana.domain.portfolio.command.CreatePortfolioCommand;
import com.porcana.domain.portfolio.command.DirectCreatePortfolioCommand;
import com.porcana.domain.portfolio.command.UpdateAssetWeightsCommand;
import com.porcana.domain.portfolio.command.UpdatePortfolioNameCommand;
import com.porcana.domain.portfolio.dto.*;
import com.porcana.domain.portfolio.dto.deck.DeckAnalysis;
import com.porcana.domain.portfolio.dto.deck.DeckAnalysisResponse;
import com.porcana.domain.portfolio.dto.deck.PositionWithAsset;
import com.porcana.domain.portfolio.entity.*;
import com.porcana.domain.portfolio.repository.*;
import com.porcana.domain.portfolio.service.deck.DeckAnalysisEngine;
import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.EnumSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioService {
    private static final EnumSet<PortfolioStatus> ADMIN_VISIBLE_PORTFOLIO_STATUSES =
            EnumSet.of(PortfolioStatus.ACTIVE, PortfolioStatus.FINISHED);

    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final PortfolioDailyReturnRepository portfolioDailyReturnRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final PortfolioReturnCalculator portfolioReturnCalculator;
    private final PortfolioSnapshotService portfolioSnapshotService;
    private final SnapshotAssetDailyReturnRepository snapshotAssetDailyReturnRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioSnapshotAssetRepository portfolioSnapshotAssetRepository;
    private final PortfolioHoldingBaselineRepository holdingBaselineRepository;
    private final HoldingBaselineService holdingBaselineService;

    private static final int MAX_GUEST_PORTFOLIOS = 3;

    // 비중 수정 제한 시간대 (배치 실행 시간)
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalTime WEIGHT_UPDATE_BLOCK_START = LocalTime.of(7, 0);
    private static final LocalTime WEIGHT_UPDATE_BLOCK_END = LocalTime.of(7, 45);
    private static final EnumSet<DayOfWeek> WEIGHT_UPDATE_BLOCK_DAYS =
            EnumSet.range(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY);

    /**
     * Get portfolios for user or guest session
     * Excludes DRAFT status portfolios (Arena in progress)
     */
    public List<PortfolioListResponse> getPortfolios(UUID userId, UUID guestSessionId) {
        List<Portfolio> portfolios;
        UUID mainPortfolioId = null;

        if (userId != null) {
            // Authenticated user - exclude DRAFT status
            User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            portfolios = portfolioRepository.findByUserIdAndStatusNotAndDeletedAtIsNullOrderByCreatedAtDesc(userId, PortfolioStatus.DRAFT);
            mainPortfolioId = user.getMainPortfolioId();
        } else if (guestSessionId != null) {
            // Guest session - exclude DRAFT status
            portfolios = portfolioRepository.findByGuestSessionIdAndStatusNotAndDeletedAtIsNullOrderByCreatedAtDesc(guestSessionId, PortfolioStatus.DRAFT);
        } else {
            throw new IllegalArgumentException("Either userId or guestSessionId must be provided");
        }

        final UUID finalMainPortfolioId = mainPortfolioId;

        return portfolios.stream()
                .map(portfolio -> {
                    Double totalReturnPct = calculateTotalReturn(portfolio.getId());
                    boolean isMain = portfolio.getId().equals(finalMainPortfolioId);
                    List<PortfolioListResponse.TopAsset> topAssets = getTopAssets(portfolio.getId());
                    return PortfolioListResponse.from(portfolio, isMain, totalReturnPct, topAssets);
                })
                .collect(Collectors.toList());
    }

    /**
     * Create portfolio for user or guest session
     */
    @Transactional
    public CreatePortfolioResponse createPortfolio(CreatePortfolioCommand command, UUID userId, UUID guestSessionId) {
        Portfolio portfolio;

        if (userId != null) {
            // Authenticated user
            portfolio = Portfolio.createForUser(userId, command.getName());
        } else if (guestSessionId != null) {
            // Guest session - check limit (excluding deleted portfolios)
            long guestPortfolioCount = portfolioRepository.countByGuestSessionIdAndDeletedAtIsNull(guestSessionId);
            if (guestPortfolioCount >= MAX_GUEST_PORTFOLIOS) {
                throw new IllegalStateException(
                    String.format("Guest users can create up to %d portfolios. Please sign up to create more.", MAX_GUEST_PORTFOLIOS)
                );
            }
            portfolio = Portfolio.createForGuest(guestSessionId, command.getName());
        } else {
            throw new IllegalArgumentException("Either userId or guestSessionId must be provided");
        }

        Portfolio saved = portfolioRepository.save(portfolio);
        return CreatePortfolioResponse.from(saved);
    }

    /**
     * 직접 종목/비중을 입력하여 포트폴리오 생성 (아레나 방식이 아닌 직접 생성)
     * - 비중이 null이면 1/n 균등 배분
     * - 비중이 있으면 합계 100% 검증 후 적용
     * - 즉시 ACTIVE 상태로 생성
     */
    @Transactional
    public CreatePortfolioResponse createPortfolioDirect(DirectCreatePortfolioCommand command, UUID userId, UUID guestSessionId) {
        Portfolio portfolio;

        if (userId != null) {
            portfolio = Portfolio.createForUser(userId, command.getName());
        } else if (guestSessionId != null) {
            long guestPortfolioCount = portfolioRepository.countByGuestSessionIdAndDeletedAtIsNull(guestSessionId);
            if (guestPortfolioCount >= MAX_GUEST_PORTFOLIOS) {
                throw new IllegalStateException(
                        String.format("Guest users can create up to %d portfolios. Please sign up to create more.", MAX_GUEST_PORTFOLIOS)
                );
            }
            portfolio = Portfolio.createForGuest(guestSessionId, command.getName());
        } else {
            throw new IllegalArgumentException("Either userId or guestSessionId must be provided");
        }

        // 비중 계산: null이면 1/n 균등 배분
        List<DirectCreatePortfolioCommand.AssetWeight> assets = command.getAssets();

        // 중복 자산 ID 검증
        Set<UUID> uniqueAssetIds = assets.stream()
                .map(DirectCreatePortfolioCommand.AssetWeight::getAssetId)
                .collect(Collectors.toSet());
        if (uniqueAssetIds.size() != assets.size()) {
            throw new IllegalArgumentException("중복된 자산 ID가 존재합니다.");
        }

        boolean hasAnyWeight = assets.stream().anyMatch(a -> a.getWeightPct() != null);

        Map<UUID, BigDecimal> weightMap = new HashMap<>();

        if (hasAnyWeight) {
            // 비중이 하나라도 있으면 모두 입력되어야 하고 합계 100% 검증
            BigDecimal totalWeight = BigDecimal.ZERO;
            for (DirectCreatePortfolioCommand.AssetWeight asset : assets) {
                if (asset.getWeightPct() == null) {
                    throw new IllegalArgumentException("비중을 입력한 경우 모든 종목의 비중을 입력해야 합니다.");
                }
                weightMap.put(asset.getAssetId(), asset.getWeightPct());
                totalWeight = totalWeight.add(asset.getWeightPct());
            }
            if (totalWeight.compareTo(BigDecimal.valueOf(100)) != 0) {
                throw new IllegalArgumentException("비중의 합계는 100%여야 합니다. 현재: " + totalWeight + "%");
            }
        } else {
            // 모두 null이면 1/n 균등 배분
            BigDecimal equalWeight = BigDecimal.valueOf(100).divide(
                    BigDecimal.valueOf(assets.size()), 2, java.math.RoundingMode.HALF_UP
            );
            BigDecimal totalAssigned = BigDecimal.ZERO;
            for (int i = 0; i < assets.size(); i++) {
                DirectCreatePortfolioCommand.AssetWeight asset = assets.get(i);
                if (i == assets.size() - 1) {
                    // 마지막 자산은 나머지 비중으로 할당하여 100% 보장
                    weightMap.put(asset.getAssetId(), BigDecimal.valueOf(100).subtract(totalAssigned));
                } else {
                    weightMap.put(asset.getAssetId(), equalWeight);
                    totalAssigned = totalAssigned.add(equalWeight);
                }
            }
        }

        // 자산 존재 여부 일괄 검증
        List<UUID> assetIds = assets.stream()
                .map(DirectCreatePortfolioCommand.AssetWeight::getAssetId)
                .toList();
        long existingCount = assetRepository.countByIdIn(assetIds);
        if (existingCount != assetIds.size()) {
            throw new IllegalArgumentException("일부 종목을 찾을 수 없습니다.");
        }

        // 포트폴리오 저장
        Portfolio saved = portfolioRepository.save(portfolio);

        // 자산 추가 (DDD: Portfolio가 PortfolioAsset 생성)
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        for (DirectCreatePortfolioCommand.AssetWeight asset : assets) {
            PortfolioAsset portfolioAsset = saved.addAsset(asset.getAssetId(), weightMap.get(asset.getAssetId()));
            portfolioAssetRepository.save(portfolioAsset);
        }

        // 스냅샷 생성 (DDD: Portfolio가 PortfolioSnapshot 생성)
        PortfolioSnapshot snapshot = saved.createSnapshot(today);
        portfolioSnapshotRepository.save(snapshot);

        // 스냅샷 자산 추가 (DDD: PortfolioSnapshot이 PortfolioSnapshotAsset 생성)
        for (DirectCreatePortfolioCommand.AssetWeight asset : assets) {
            PortfolioSnapshotAsset snapshotAsset = snapshot.addAsset(asset.getAssetId(), weightMap.get(asset.getAssetId()));
            portfolioSnapshotAssetRepository.save(snapshotAsset);
        }

        // ACTIVE 상태로 변경
        saved.activate();
        portfolioRepository.save(saved);

        return CreatePortfolioResponse.from(saved);
    }

    /**
     * Get portfolio details (supports both user and guest)
     */
    public PortfolioDetailResponse getPortfolio(UUID portfolioId, UUID userId, UUID guestSessionId) {
        Portfolio portfolio = findPortfolioWithOwnership(portfolioId, userId, guestSessionId);

        boolean isMain = false;
        if (userId != null) {
            User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            isMain = portfolio.getId().equals(user.getMainPortfolioId());
        }

        Double totalReturnPct = calculateTotalReturn(portfolio.getId());
        List<PortfolioDetailResponse.PositionInfo> positions = buildPositions(portfolio.getId());

        // Calculate portfolio-level risk metrics
        Double averageRiskLevel = calculateAverageRiskLevel(positions);
        String diversityLevel = calculateDiversityLevel(portfolio.getId());
        Map<Integer, Double> riskDistribution = calculateRiskDistribution(positions);

        // Get baseline summary if exists
        PortfolioDetailResponse.BaselineSummary baselineSummary =
                holdingBaselineService.getBaselineSummaryInternal(portfolio.getId());

        return PortfolioDetailResponse.from(
            portfolio,
            isMain,
            totalReturnPct,
            averageRiskLevel,
            diversityLevel,
            riskDistribution,
            positions,
            baselineSummary
        );
    }

    /**
     * Start portfolio (supports both user and guest)
     */
    @Transactional
    public StartPortfolioResponse startPortfolio(UUID portfolioId, UUID userId, UUID guestSessionId) {
        Portfolio portfolio = findPortfolioWithOwnership(portfolioId, userId, guestSessionId);

        if (portfolio.getStatus() != PortfolioStatus.DRAFT) {
            throw new IllegalStateException("Portfolio is already started");
        }

        portfolio.start();
        Portfolio saved = portfolioRepository.save(portfolio);
        return StartPortfolioResponse.from(saved);
    }

    /**
     * Get portfolio performance (supports both user and guest)
     */
    public PortfolioPerformanceResponse getPortfolioPerformance(UUID portfolioId, UUID userId, UUID guestSessionId, String range) {
        Portfolio portfolio = findPortfolioWithOwnership(portfolioId, userId, guestSessionId);
        return buildPortfolioPerformance(portfolio, range);
    }

    /**
     * Get portfolio performance for admin (no ownership check)
     */
    public PortfolioPerformanceResponse getAdminPortfolioPerformance(UUID portfolioId, String range) {
        Portfolio portfolio = portfolioRepository.findByIdAndDeletedAtIsNull(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found"));

        if (!ADMIN_VISIBLE_PORTFOLIO_STATUSES.contains(portfolio.getStatus())) {
            throw new IllegalArgumentException(
                    "Admin portfolio performance supports only ACTIVE or FINISHED portfolios: " + portfolioId
            );
        }

        return buildPortfolioPerformance(portfolio, range);
    }

    private PortfolioPerformanceResponse buildPortfolioPerformance(Portfolio portfolio, String range) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = calculateStartDate(endDate, range);

        List<PortfolioDailyReturn> returns = portfolioDailyReturnRepository
                .findByPortfolioIdAndReturnDateBetweenOrderByReturnDateAsc(portfolio.getId(), startDate, endDate);

        List<PortfolioPerformanceResponse.PerformancePoint> points = new ArrayList<>();

        // Determine initial date
        LocalDate initialDate = startDate;
        if (portfolio.getStartedAt() != null && portfolio.getStartedAt().isAfter(startDate)) {
            initialDate = portfolio.getStartedAt();
        }

        // Start with 100 at the initial date
        points.add(PortfolioPerformanceResponse.PerformancePoint.builder()
                .date(initialDate)
                .value(100.0)
                .build());

        for (PortfolioReturnCalculator.PortfolioValuePoint point :
                portfolioReturnCalculator.calculatePortfolioValueSeries(returns, 100.0)) {
            if (point.date().isBefore(initialDate) || point.date().isEqual(initialDate)) {
                continue;
            }

            points.add(PortfolioPerformanceResponse.PerformancePoint.builder()
                    .date(point.date())
                    .value(point.value())
                    .build());
        }

        return PortfolioPerformanceResponse.from(portfolio, range, points);
    }

    private Double calculateTotalReturn(UUID portfolioId) {
        return portfolioReturnCalculator.calculateTotalReturn(portfolioId);
    }

    private List<PortfolioDetailResponse.PositionInfo> buildPositions(UUID portfolioId) {
        List<PortfolioAsset> portfolioAssets = portfolioAssetRepository.findByPortfolioId(portfolioId);

        if (portfolioAssets.isEmpty()) {
            return Collections.emptyList();
        }

        // Load all assets
        Set<UUID> assetIds = portfolioAssets.stream()
                .map(PortfolioAsset::getAssetId)
                .collect(Collectors.toSet());

        Map<UUID, Asset> assetMap = assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, asset -> asset));

        // Calculate individual asset returns
        Map<UUID, Double> assetReturns = portfolioReturnCalculator.calculateAssetReturns(portfolioId, assetIds);

        // Get latest market-cap based weights
        Map<UUID, Double> latestWeights = getLatestWeights(portfolioId, assetIds);

        // Get snapshot target weights
        Map<UUID, Double> snapshotWeights = getSnapshotWeights(portfolioId, assetIds);

        return portfolioAssets.stream()
                .map(pa -> {
                    Asset asset = assetMap.get(pa.getAssetId());
                    if (asset == null) {
                        return null;
                    }

                    Double returnPct = assetReturns.getOrDefault(pa.getAssetId(), 0.0);
                    // Use latest market-cap based weight, fallback to initial weight if not available
                    Double weightPct = latestWeights.getOrDefault(pa.getAssetId(), pa.getWeightPct().doubleValue());
                    // Snapshot target weight
                    Double targetWeightPct = snapshotWeights.getOrDefault(pa.getAssetId(), pa.getWeightPct().doubleValue());

                    return PortfolioDetailResponse.PositionInfo.builder()
                            .assetId(asset.getId().toString())
                            .ticker(asset.getSymbol())
                            .name(asset.getName())
                            .currentRiskLevel(asset.getCurrentRiskLevel())
                            .imageUrl(asset.getImageUrl())
                            .weightPct(weightPct)
                            .targetWeightPct(targetWeightPct)
                            .returnPct(returnPct)
                            .build();
                })
                .filter(Objects::nonNull)
                .sorted((p1, p2) -> Double.compare(p2.getWeightPct(), p1.getWeightPct())) // Sort by weight descending
                .collect(Collectors.toList());
    }

    /**
     * Get latest market-cap based weights for assets
     * Priority:
     * 1. Most recent weightUsed from SnapshotAssetDailyReturn (if exists for latest snapshot)
     * 2. Latest PortfolioSnapshotAsset weight (after rebalancing)
     * 3. PortfolioAsset weight (fallback in buildPositions)
     */
    private Map<UUID, Double> getLatestWeights(UUID portfolioId, Set<UUID> assetIds) {
        Map<UUID, Double> weights = new HashMap<>();

        // Get the latest snapshot
        Optional<PortfolioSnapshot> latestSnapshotOpt = portfolioSnapshotRepository
                .findFirstByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        portfolioId, LocalDate.now());

        if (latestSnapshotOpt.isEmpty()) {
            return weights; // No snapshot yet, will fallback to PortfolioAsset weights
        }

        PortfolioSnapshot latestSnapshot = latestSnapshotOpt.get();

        // Get snapshot assets for the latest snapshot (this includes rebalanced weights)
        List<PortfolioSnapshotAsset> snapshotAssets = portfolioSnapshotAssetRepository
                .findBySnapshotId(latestSnapshot.getId());
        Map<UUID, BigDecimal> snapshotWeightMap = snapshotAssets.stream()
                .collect(Collectors.toMap(
                        PortfolioSnapshotAsset::getAssetId,
                        PortfolioSnapshotAsset::getWeight
                ));

        // For each asset, try to get the most recent weightUsed from daily returns
        // If daily return exists for the latest snapshot, use it (market-adjusted weight)
        // Otherwise, use the snapshot weight (rebalanced weight)
        for (UUID assetId : assetIds) {
            Optional<SnapshotAssetDailyReturn> dailyReturnOpt = snapshotAssetDailyReturnRepository
                    .findFirstByPortfolioIdAndAssetIdOrderByReturnDateDesc(portfolioId, assetId);

            if (dailyReturnOpt.isPresent()) {
                SnapshotAssetDailyReturn dailyReturn = dailyReturnOpt.get();
                // Only use daily return if it's from the current snapshot (or later)
                if (!dailyReturn.getReturnDate().isBefore(latestSnapshot.getEffectiveDate())) {
                    weights.put(assetId, dailyReturn.getWeightUsed().doubleValue());
                    continue;
                }
            }

            // Fallback to snapshot weight (after rebalancing)
            if (snapshotWeightMap.containsKey(assetId)) {
                weights.put(assetId, snapshotWeightMap.get(assetId).doubleValue());
            }
        }

        return weights;
    }

    /**
     * Get snapshot target weights for assets
     * Returns the weights set in the latest snapshot (target/initial allocation)
     */
    private Map<UUID, Double> getSnapshotWeights(UUID portfolioId, Set<UUID> assetIds) {
        Map<UUID, Double> weights = new HashMap<>();

        // Get the latest snapshot
        Optional<PortfolioSnapshot> latestSnapshotOpt = portfolioSnapshotRepository
                .findFirstByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
                        portfolioId, LocalDate.now());

        if (latestSnapshotOpt.isEmpty()) {
            return weights;
        }

        // Get snapshot assets
        List<PortfolioSnapshotAsset> snapshotAssets = portfolioSnapshotAssetRepository
                .findBySnapshotId(latestSnapshotOpt.get().getId());

        for (PortfolioSnapshotAsset sa : snapshotAssets) {
            if (assetIds.contains(sa.getAssetId())) {
                weights.put(sa.getAssetId(), sa.getWeight().doubleValue());
            }
        }

        return weights;
    }

    /**
     * Calculate weighted average risk level
     */
    private Double calculateAverageRiskLevel(List<PortfolioDetailResponse.PositionInfo> positions) {
        if (positions.isEmpty()) {
            return null;
        }

        double totalWeight = 0.0;
        double weightedRiskSum = 0.0;

        for (PortfolioDetailResponse.PositionInfo position : positions) {
            if (position.getCurrentRiskLevel() != null && position.getWeightPct() != null) {
                double weight = position.getWeightPct() / 100.0;  // Convert percentage to decimal
                weightedRiskSum += position.getCurrentRiskLevel() * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight == 0.0) {
            return null;
        }

        // Round to 1 decimal place
        return Math.round(weightedRiskSum / totalWeight * 10.0) / 10.0;
    }

    /**
     * Calculate diversity level based on sector and risk distribution
     */
    private String calculateDiversityLevel(UUID portfolioId) {
        List<PortfolioAsset> portfolioAssets = portfolioAssetRepository.findByPortfolioId(portfolioId);

        if (portfolioAssets.isEmpty()) {
            return "LOW";
        }

        Set<UUID> assetIds = portfolioAssets.stream()
                .map(PortfolioAsset::getAssetId)
                .collect(Collectors.toSet());

        List<Asset> assets = assetRepository.findAllById(assetIds);

        // 1. Sector diversity (섹터 다양성)
        long distinctSectors = assets.stream()
                .map(Asset::getSector)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        // 2. Risk band diversity (위험도 밴드 다양성)
        long distinctRiskBands = assets.stream()
                .map(Asset::getCurrentRiskLevel)
                .filter(Objects::nonNull)
                .map(this::getRiskBand)
                .distinct()
                .count();

        // 3. Asset type diversity (자산 타입 다양성)
        long distinctAssetTypes = assets.stream()
                .map(Asset::getType)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        // Calculate diversity score (0-100)
        int totalAssets = assets.size();
        double sectorScore = totalAssets > 0 ? (distinctSectors * 100.0) / Math.min(totalAssets, 5) : 0;  // Max 5 sectors
        double riskScore = (distinctRiskBands * 100.0) / 3;  // Max 3 bands (LOW, MID, HIGH)
        double typeScore = (distinctAssetTypes * 100.0) / 2;  // Max 2 types (STOCK, ETF)

        double diversityScore = (sectorScore * 0.5) + (riskScore * 0.3) + (typeScore * 0.2);

        // Map to level
        if (diversityScore >= 70) {
            return "HIGH";
        } else if (diversityScore >= 40) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    /**
     * Calculate risk distribution by risk level (1-5)
     * Returns percentage weight for each risk level
     */
    private Map<Integer, Double> calculateRiskDistribution(List<PortfolioDetailResponse.PositionInfo> positions) {
        Map<Integer, Double> distribution = new HashMap<>();

        // Initialize all risk levels to 0%
        for (int level = 1; level <= 5; level++) {
            distribution.put(level, 0.0);
        }

        if (positions.isEmpty()) {
            return distribution;
        }

        // Group positions by risk level and sum their weights
        for (PortfolioDetailResponse.PositionInfo position : positions) {
            if (position.getCurrentRiskLevel() != null && position.getWeightPct() != null) {
                Integer riskLevel = position.getCurrentRiskLevel();
                Double currentWeight = distribution.getOrDefault(riskLevel, 0.0);
                distribution.put(riskLevel, currentWeight + position.getWeightPct());
            }
        }

        // Round to 2 decimal places
        for (Map.Entry<Integer, Double> entry : distribution.entrySet()) {
            double rounded = Math.round(entry.getValue() * 100.0) / 100.0;
            distribution.put(entry.getKey(), rounded);
        }

        return distribution;
    }

    /**
     * Map risk level to risk band
     */
    private String getRiskBand(Integer riskLevel) {
        if (riskLevel == null) {
            return "UNKNOWN";
        }
        if (riskLevel <= 2) {
            return "LOW";
        } else if (riskLevel == 3) {
            return "MEDIUM";
        } else {
            return "HIGH";
        }
    }

    /**
     * Get top 3 assets by weight for portfolio list
     * Uses QueryDSL to fetch latest market-cap based weights in a single query
     * Falls back to PortfolioAsset if no daily return data exists
     */
    private List<PortfolioListResponse.TopAsset> getTopAssets(UUID portfolioId) {
        List<PortfolioAsset> portfolioAssets = portfolioAssetRepository.findByPortfolioId(portfolioId);

        if (portfolioAssets.isEmpty()) {
            return Collections.emptyList();
        }

        // Get latest weights using the same logic as buildPositions
        Set<UUID> assetIds = portfolioAssets.stream()
                .map(PortfolioAsset::getAssetId)
                .collect(Collectors.toSet());

        Map<UUID, Double> latestWeights = getLatestWeights(portfolioId, assetIds);

        // Load asset information
        Map<UUID, Asset> assetMap = assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, asset -> asset));

        // Build TopAsset DTOs with latest weights
        return portfolioAssets.stream()
                .map(pa -> {
                    Asset asset = assetMap.get(pa.getAssetId());
                    if (asset == null) {
                        return null;
                    }

                    // Use latest weight, fallback to PortfolioAsset weight if not available
                    Double weight = latestWeights.getOrDefault(pa.getAssetId(), pa.getWeightPct().doubleValue());

                    return new PortfolioListResponse.TopAsset(
                            asset.getId(),
                            asset.getSymbol(),
                            asset.getName(),
                            asset.getImageUrl(),
                            BigDecimal.valueOf(weight)
                    );
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> b.weight().compareTo(a.weight())) // Sort by weight descending
                .limit(3)
                .collect(Collectors.toList());
    }

    /**
     * Get top assets by weight from PortfolioAsset (fallback method)
     * Used when no daily return data is available yet (e.g., after weight update)
     */
    private List<PortfolioListResponse.TopAsset> getTopAssetsFromPortfolioAsset(UUID portfolioId, int limit) {
        List<PortfolioAsset> portfolioAssets = portfolioAssetRepository.findByPortfolioId(portfolioId);

        if (portfolioAssets.isEmpty()) {
            return Collections.emptyList();
        }

        // Sort by weight descending and take top N
        List<PortfolioAsset> sortedAssets = portfolioAssets.stream()
            .sorted((a, b) -> b.getWeightPct().compareTo(a.getWeightPct()))
            .limit(limit)
            .toList();

        // Load asset information
        Set<UUID> assetIds = sortedAssets.stream()
            .map(PortfolioAsset::getAssetId)
            .collect(Collectors.toSet());

        Map<UUID, Asset> assetMap = assetRepository.findAllById(assetIds).stream()
            .collect(Collectors.toMap(Asset::getId, asset -> asset));

        // Build TopAsset DTOs
        return sortedAssets.stream()
            .map(pa -> {
                Asset asset = assetMap.get(pa.getAssetId());
                if (asset == null) {
                    return null;
                }

                return new PortfolioListResponse.TopAsset(
                    asset.getId(),
                    asset.getSymbol(),
                    asset.getName(),
                    asset.getImageUrl(),
                    pa.getWeightPct()
                );
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    @Transactional
    public UpdatePortfolioNameResponse updatePortfolioName(UpdatePortfolioNameCommand command) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserIdAndDeletedAtIsNull(command.getPortfolioId(), command.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found or access denied"));

        portfolio.updateName(command.getName());
        Portfolio saved = portfolioRepository.save(portfolio);
        return UpdatePortfolioNameResponse.from(saved);
    }

    /**
     * Delete portfolio (soft delete)
     * Main portfolio cannot be deleted - must change main portfolio first
     */
    @Transactional
    public void deletePortfolio(UUID portfolioId, UUID userId, UUID guestSessionId) {
        Portfolio portfolio = findPortfolioWithOwnership(portfolioId, userId, guestSessionId);

        // 메인 포트폴리오는 삭제 불가 - 먼저 메인을 변경해야 함
        if (userId != null) {
            User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            if (portfolioId.equals(user.getMainPortfolioId())) {
                throw new IllegalStateException("Cannot delete main portfolio. Please change main portfolio first.");
            }
        }

        portfolio.delete();
        portfolioRepository.save(portfolio);
    }

    /**
     * Delete all portfolios for a user (soft delete)
     * Called when user withdraws from the service
     */
    @Transactional
    public void deleteAllPortfoliosForUser(UUID userId) {
        List<Portfolio> portfolios = portfolioRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);
        for (Portfolio portfolio : portfolios) {
            portfolio.delete();
        }
        portfolioRepository.saveAll(portfolios);
    }

    @Transactional
    public UpdateAssetWeightsResponse updateAssetWeights(UpdateAssetWeightsCommand command) {
        // 배치 실행 시간대(07:00~07:45 KST)에는 비중 수정 불가
        validateNotInBatchWindow();

        Portfolio portfolio = portfolioRepository.findByIdAndUserIdAndDeletedAtIsNull(command.getPortfolioId(), command.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found or access denied"));

        // 비중 합계 검증 (100%가 되어야 함)
        java.math.BigDecimal totalWeight = command.getWeights().stream()
                .map(UpdateAssetWeightsCommand.AssetWeightUpdate::getWeightPct)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        if (totalWeight.compareTo(java.math.BigDecimal.valueOf(100.0)) != 0) {
            throw new IllegalArgumentException("Total weight must be 100%. Current total: " + totalWeight + "%");
        }

        // 기존 자산들 조회
        List<PortfolioAsset> existingAssets = portfolioAssetRepository.findByPortfolioId(command.getPortfolioId());
        Map<UUID, PortfolioAsset> existingAssetMap = existingAssets.stream()
                .collect(Collectors.toMap(PortfolioAsset::getAssetId, pa -> pa));

        // 새로운 비중으로 업데이트
        List<UpdateAssetWeightsResponse.AssetWeightInfo> updatedWeights = new ArrayList<>();
        for (UpdateAssetWeightsCommand.AssetWeightUpdate weightUpdate : command.getWeights()) {
            PortfolioAsset portfolioAsset = existingAssetMap.get(weightUpdate.getAssetId());
            if (portfolioAsset == null) {
                throw new IllegalArgumentException("Asset not found in portfolio: " + weightUpdate.getAssetId());
            }

            portfolioAsset.setWeightPct(weightUpdate.getWeightPct());
            portfolioAssetRepository.save(portfolioAsset);

            updatedWeights.add(UpdateAssetWeightsResponse.AssetWeightInfo.builder()
                    .assetId(portfolioAsset.getAssetId().toString())
                    .weightPct(portfolioAsset.getWeightPct().doubleValue())
                    .build());
        }

        // Create snapshot for the weight update
        LocalDate today = LocalDate.now();
        Map<UUID, BigDecimal> weightMap = command.getWeights().stream()
                .collect(Collectors.toMap(
                        UpdateAssetWeightsCommand.AssetWeightUpdate::getAssetId,
                        UpdateAssetWeightsCommand.AssetWeightUpdate::getWeightPct
                ));
        portfolioSnapshotService.createSnapshotWithAssets(
                command.getPortfolioId(),
                weightMap,
                today,
                "Portfolio rebalancing"
        );

        // baseline에도 목표 비중 반영
        if (command.isApplyToBaseline()) {
            holdingBaselineRepository.findByPortfolioIdWithItems(command.getPortfolioId())
                    .ifPresent(baseline -> {
                        Map<UUID, PortfolioHoldingBaselineItem> baselineItemMap = baseline.getItems().stream()
                                .collect(Collectors.toMap(PortfolioHoldingBaselineItem::getAssetId, item -> item));

                        for (UpdateAssetWeightsCommand.AssetWeightUpdate weightUpdate : command.getWeights()) {
                            PortfolioHoldingBaselineItem item = baselineItemMap.get(weightUpdate.getAssetId());
                            if (item == null) {
                                throw new IllegalArgumentException(
                                        "Baseline item not found for asset: " + weightUpdate.getAssetId()
                                );
                            }
                            item.updateTargetWeight(weightUpdate.getWeightPct());

                        }
                        holdingBaselineRepository.save(baseline);
                    });
        }

        return UpdateAssetWeightsResponse.from(portfolio, updatedWeights);
    }

    private LocalDate calculateStartDate(LocalDate endDate, String range) {
        if (range == null || range.isBlank()) {
            return endDate.minusMonths(1);
        }

        return switch (range) {
            case "3M" -> endDate.minusMonths(3);
            case "1Y" -> endDate.minusYears(1);
            default -> endDate.minusMonths(1);
        };
    }

    /**
     * 배치 실행 시간대(07:00~07:45 KST)에는 비중 수정을 막음
     * 수익률 계산 배치가 07:00~07:30에 실행되므로 데이터 정합성을 위해 제한
     */
    private void validateNotInBatchWindow() {
        ZonedDateTime now = ZonedDateTime.now(SEOUL);
        LocalTime currentTime = now.toLocalTime();
        DayOfWeek dayOfWeek = now.getDayOfWeek();

        boolean isBatchDay = WEIGHT_UPDATE_BLOCK_DAYS.contains(dayOfWeek);
        boolean isInBlockTime = !currentTime.isBefore(WEIGHT_UPDATE_BLOCK_START)
                && currentTime.isBefore(WEIGHT_UPDATE_BLOCK_END);

        if (isBatchDay && isInBlockTime) {
            throw new IllegalStateException(
                    "비중 수정은 오전 7:00~7:45 사이에 불가능합니다. 수익률 업데이트 중입니다."
            );
        }
    }

    /**
     * 포트폴리오 덱 분석
     */
    public DeckAnalysisResponse getDeckAnalysis(UUID portfolioId, UUID userId, UUID guestSessionId) {
        Portfolio portfolio = findPortfolioWithOwnership(portfolioId, userId, guestSessionId);

        // 포트폴리오 자산 조회
        List<PortfolioAsset> portfolioAssets = portfolioAssetRepository.findByPortfolioId(portfolioId);
        if (portfolioAssets.isEmpty()) {
            return DeckAnalysisResponse.from(portfolioId, DeckAnalysisEngine.analyze(List.of()));
        }

        // 자산 정보 조회
        Set<UUID> assetIds = portfolioAssets.stream()
                .map(PortfolioAsset::getAssetId)
                .collect(Collectors.toSet());
        Map<UUID, Asset> assetMap = assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, a -> a));

        // 최신 비중 조회 (스냅샷 기반)
        Map<UUID, Double> latestWeights = getDeckLatestWeights(portfolioId, assetIds);

        // PositionWithAsset 목록 생성
        List<PositionWithAsset> positions = portfolioAssets.stream()
                .map(pa -> {
                    Asset asset = assetMap.get(pa.getAssetId());
                    if (asset == null) return null;

                    // 비중: 스냅샷 기반 또는 PortfolioAsset 기반
                    Double weight = latestWeights.getOrDefault(pa.getAssetId(),
                            pa.getWeightPct() != null ? pa.getWeightPct().doubleValue() : 0.0);

                    // 자산 성격 계산
                    AssetPersonality personality = AssetPersonalityRuleEngine.compute(asset);

                    return new PositionWithAsset(asset, weight, personality);
                })
                .filter(Objects::nonNull)
                .toList();

        // 덱 분석 실행
        DeckAnalysis analysis = DeckAnalysisEngine.analyze(positions);
        return DeckAnalysisResponse.from(portfolioId, analysis);
    }

    /**
     * 덱 분석용 최신 스냅샷 기반 비중 조회
     */
    private Map<UUID, Double> getDeckLatestWeights(UUID portfolioId, Set<UUID> assetIds) {
        Optional<PortfolioSnapshot> latestSnapshot = portfolioSnapshotRepository
                .findFirstByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(portfolioId, LocalDate.now());

        if (latestSnapshot.isEmpty()) {
            return Map.of();
        }

        List<PortfolioSnapshotAsset> snapshotAssets = portfolioSnapshotAssetRepository
                .findBySnapshotId(latestSnapshot.get().getId());

        return snapshotAssets.stream()
                .filter(sa -> assetIds.contains(sa.getAssetId()))
                .collect(Collectors.toMap(
                        PortfolioSnapshotAsset::getAssetId,
                        sa -> sa.getWeight().doubleValue(),
                        (a, b) -> a
                ));
    }

    /**
     * Find portfolio with ownership validation (supports both user and guest, excludes deleted)
     * @param portfolioId Portfolio ID
     * @param userId User ID (nullable)
     * @param guestSessionId Guest session ID (nullable)
     * @return Portfolio if found and authorized
     * @throws IllegalArgumentException if portfolio not found or access denied
     */
    private Portfolio findPortfolioWithOwnership(UUID portfolioId, UUID userId, UUID guestSessionId) {
        if (userId != null) {
            // Authenticated user
            return portfolioRepository.findByIdAndUserIdAndDeletedAtIsNull(portfolioId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Portfolio not found or access denied"));
        } else if (guestSessionId != null) {
            // Guest session
            return portfolioRepository.findByIdAndGuestSessionIdAndDeletedAtIsNull(portfolioId, guestSessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Portfolio not found or access denied"));
        } else {
            throw new IllegalArgumentException("Either userId or guestSessionId must be provided");
        }
    }
}
