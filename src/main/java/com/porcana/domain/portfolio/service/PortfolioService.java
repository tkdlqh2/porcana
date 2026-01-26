package com.porcana.domain.portfolio.service;

import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.portfolio.command.CreatePortfolioCommand;
import com.porcana.domain.portfolio.command.UpdateAssetWeightsCommand;
import com.porcana.domain.portfolio.command.UpdatePortfolioNameCommand;
import com.porcana.domain.portfolio.dto.*;
import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioAsset;
import com.porcana.domain.portfolio.entity.PortfolioDailyReturn;
import com.porcana.domain.portfolio.entity.PortfolioStatus;
import com.porcana.domain.portfolio.repository.PortfolioAssetRepository;
import com.porcana.domain.portfolio.repository.PortfolioDailyReturnRepository;
import com.porcana.domain.portfolio.repository.PortfolioRepository;
import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final PortfolioDailyReturnRepository portfolioDailyReturnRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final PortfolioReturnCalculator portfolioReturnCalculator;
    private final PortfolioSnapshotService portfolioSnapshotService;

    private static final int MAX_GUEST_PORTFOLIOS = 3;

    /**
     * Get portfolios for user or guest session
     */
    public List<PortfolioListResponse> getPortfolios(UUID userId, UUID guestSessionId) {
        List<Portfolio> portfolios;
        UUID mainPortfolioId = null;

        if (userId != null) {
            // Authenticated user
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            portfolios = portfolioRepository.findByUserIdOrderByCreatedAtDesc(userId);
            mainPortfolioId = user.getMainPortfolioId();
        } else if (guestSessionId != null) {
            // Guest session
            portfolios = portfolioRepository.findByGuestSessionIdOrderByCreatedAtDesc(guestSessionId);
        } else {
            throw new IllegalArgumentException("Either userId or guestSessionId must be provided");
        }

        final UUID finalMainPortfolioId = mainPortfolioId;

        return portfolios.stream()
                .map(portfolio -> {
                    Double totalReturnPct = calculateTotalReturn(portfolio.getId());
                    boolean isMain = portfolio.getId().equals(finalMainPortfolioId);
                    return PortfolioListResponse.from(portfolio, isMain, totalReturnPct);
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
            // Guest session - check limit
            long guestPortfolioCount = portfolioRepository.countByGuestSessionId(guestSessionId);
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
     * Get portfolio details (supports both user and guest)
     */
    public PortfolioDetailResponse getPortfolio(UUID portfolioId, UUID userId, UUID guestSessionId) {
        Portfolio portfolio = findPortfolioWithOwnership(portfolioId, userId, guestSessionId);

        boolean isMain = false;
        if (userId != null) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            isMain = portfolio.getId().equals(user.getMainPortfolioId());
        }

        Double totalReturnPct = calculateTotalReturn(portfolio.getId());
        List<PortfolioDetailResponse.PositionInfo> positions = buildPositions(portfolio.getId());

        // Calculate portfolio-level risk metrics
        Double averageRiskLevel = calculateAverageRiskLevel(positions);
        String diversityLevel = calculateDiversityLevel(portfolio.getId());
        Map<Integer, Double> riskDistribution = calculateRiskDistribution(positions);

        return PortfolioDetailResponse.from(
            portfolio,
            isMain,
            totalReturnPct,
            averageRiskLevel,
            diversityLevel,
            riskDistribution,
            positions
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

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = calculateStartDate(endDate, range);

        List<PortfolioDailyReturn> returns = portfolioDailyReturnRepository
                .findByPortfolioIdAndReturnDateBetweenOrderByReturnDateAsc(portfolioId, startDate, endDate);

        List<PortfolioPerformanceResponse.PerformancePoint> points = new ArrayList<>();

        // Start with 100 at the start date
        double cumulativeValue = 100.0;
        LocalDate initialDate = startDate;
        if (portfolio.getStartedAt() != null && portfolio.getStartedAt().isAfter(startDate)) {
            initialDate = portfolio.getStartedAt();
        }
        points.add(PortfolioPerformanceResponse.PerformancePoint.builder()
                .date(initialDate)
                .value(100.0)
                .build());

        // Calculate cumulative values
        for (PortfolioDailyReturn dailyReturn : returns) {
            if (dailyReturn.getReturnDate().isBefore(initialDate)) {
                continue;
            }
            double dailyReturnValue = dailyReturn.getReturnTotal().doubleValue() / 100.0;
            cumulativeValue *= (1.0 + dailyReturnValue);

            points.add(PortfolioPerformanceResponse.PerformancePoint.builder()
                    .date(dailyReturn.getReturnDate())
                    .value(cumulativeValue)
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

        return portfolioAssets.stream()
                .map(pa -> {
                    Asset asset = assetMap.get(pa.getAssetId());
                    if (asset == null) {
                        return null;
                    }

                    Double returnPct = assetReturns.getOrDefault(pa.getAssetId(), 0.0);

                    return PortfolioDetailResponse.PositionInfo.builder()
                            .assetId(asset.getId().toString())
                            .ticker(asset.getSymbol())
                            .name(asset.getName())
                            .currentRiskLevel(asset.getCurrentRiskLevel())
                            .imageUrl(asset.getImageUrl())
                            .weightPct(pa.getWeightPct().doubleValue())
                            .returnPct(returnPct)
                            .build();
                })
                .filter(Objects::nonNull)
                .sorted((p1, p2) -> Double.compare(p2.getWeightPct(), p1.getWeightPct())) // Sort by weight descending
                .collect(Collectors.toList());
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

    @Transactional
    public UpdatePortfolioNameResponse updatePortfolioName(UpdatePortfolioNameCommand command) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(command.getPortfolioId(), command.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found or access denied"));

        portfolio.updateName(command.getName());
        Portfolio saved = portfolioRepository.save(portfolio);
        return UpdatePortfolioNameResponse.from(saved);
    }

    @Transactional
    public UpdateAssetWeightsResponse updateAssetWeights(UpdateAssetWeightsCommand command) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(command.getPortfolioId(), command.getUserId())
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
     * Find portfolio with ownership validation (supports both user and guest)
     * @param portfolioId Portfolio ID
     * @param userId User ID (nullable)
     * @param guestSessionId Guest session ID (nullable)
     * @return Portfolio if found and authorized
     * @throws IllegalArgumentException if portfolio not found or access denied
     */
    private Portfolio findPortfolioWithOwnership(UUID portfolioId, UUID userId, UUID guestSessionId) {
        if (userId != null) {
            // Authenticated user
            return portfolioRepository.findByIdAndUserId(portfolioId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Portfolio not found or access denied"));
        } else if (guestSessionId != null) {
            // Guest session
            return portfolioRepository.findByIdAndGuestSessionId(portfolioId, guestSessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Portfolio not found or access denied"));
        } else {
            throw new IllegalArgumentException("Either userId or guestSessionId must be provided");
        }
    }
}