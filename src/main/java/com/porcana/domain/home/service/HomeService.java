package com.porcana.domain.home.service;

import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.home.dto.HomeResponse;
import com.porcana.domain.home.dto.MainPortfolioIdResponse;
import com.porcana.domain.portfolio.entity.*;
import com.porcana.domain.portfolio.repository.*;
import com.porcana.domain.portfolio.service.PortfolioReturnCalculator;
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
public class HomeService {

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final PortfolioDailyReturnRepository portfolioDailyReturnRepository;
    private final AssetRepository assetRepository;
    private final PortfolioReturnCalculator portfolioReturnCalculator;
    private final SnapshotAssetDailyReturnRepository snapshotAssetDailyReturnRepository;
    private final PortfolioSnapshotRepository portfolioSnapshotRepository;
    private final PortfolioSnapshotAssetRepository portfolioSnapshotAssetRepository;

    public HomeResponse getHome(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UUID mainPortfolioId = user.getMainPortfolioId();

        if (mainPortfolioId == null) {
            return HomeResponse.noMainPortfolio();
        }

        Portfolio portfolio = portfolioRepository.findById(mainPortfolioId)
                .orElse(null);

        if (portfolio == null) {
            return HomeResponse.noMainPortfolio();
        }

        // Calculate total return
        Double totalReturnPct = calculateTotalReturn(portfolio.getId());

        // Build main portfolio info
        HomeResponse.MainPortfolioInfo mainPortfolioInfo = HomeResponse.MainPortfolioInfo.builder()
                .portfolioId(portfolio.getId().toString())
                .name(portfolio.getName())
                .startedAt(portfolio.getStartedAt())
                .totalReturnPct(totalReturnPct)
                .build();

        // Build chart data
        List<HomeResponse.ChartPoint> chart = buildChartData(portfolio.getId());

        // Build positions
        List<HomeResponse.PositionInfo> positions = buildPositions(portfolio.getId());

        return HomeResponse.builder()
                .hasMainPortfolio(true)
                .mainPortfolio(mainPortfolioInfo)
                .chart(chart)
                .positions(positions)
                .build();
    }

    @Transactional
    public MainPortfolioIdResponse setMainPortfolio(UUID userId, UUID portfolioId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found or access denied"));

        user.setMainPortfolioId(portfolio.getId());
        userRepository.save(user);

        return MainPortfolioIdResponse.builder()
                .mainPortfolioId(portfolio.getId())
                .build();
    }

    private Double calculateTotalReturn(UUID portfolioId) {
        return portfolioReturnCalculator.calculateTotalReturn(portfolioId);
    }

    private List<HomeResponse.ChartPoint> buildChartData(UUID portfolioId) {
        List<PortfolioDailyReturn> returns = portfolioDailyReturnRepository.findByPortfolioIdOrderByReturnDateAsc(portfolioId);

        if (returns.isEmpty()) {
            return Collections.emptyList();
        }

        List<HomeResponse.ChartPoint> chartPoints = new ArrayList<>();
        double cumulativeValue = 100.0;

        // Start with 100 at the first date
        Portfolio portfolio = portfolioRepository.findById(portfolioId).orElseThrow();
        LocalDate startDate = portfolio.getStartedAt();
        if (startDate != null && (returns.isEmpty() || !returns.get(0).getReturnDate().equals(startDate))) {
            chartPoints.add(HomeResponse.ChartPoint.builder()
                    .date(startDate)
                    .value(100.0)
                    .build());
        }

        // Calculate cumulative values
        for (PortfolioDailyReturn dailyReturn : returns) {
            double dailyReturnValue = dailyReturn.getReturnTotal().doubleValue() / 100.0;
            cumulativeValue *= (1.0 + dailyReturnValue);

            chartPoints.add(HomeResponse.ChartPoint.builder()
                    .date(dailyReturn.getReturnDate())
                    .value(cumulativeValue)
                    .build());
        }

        return chartPoints;
    }

    private List<HomeResponse.PositionInfo> buildPositions(UUID portfolioId) {
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
        Map<UUID, Double> assetReturns = calculateAssetReturns(portfolioId, assetIds);

        // Get latest market-cap based weights
        Map<UUID, Double> latestWeights = getLatestWeights(portfolioId, assetIds);

        return portfolioAssets.stream()
                .map(pa -> {
                    Asset asset = assetMap.get(pa.getAssetId());
                    if (asset == null) {
                        return null;
                    }

                    Double returnPct = assetReturns.getOrDefault(pa.getAssetId(), 0.0);
                    // Use latest market-cap based weight, fallback to initial weight if not available
                    Double weightPct = latestWeights.getOrDefault(pa.getAssetId(), pa.getWeightPct().doubleValue());

                    return HomeResponse.PositionInfo.builder()
                            .assetId(asset.getId().toString())
                            .ticker(asset.getSymbol())
                            .name(asset.getName())
                            .imageUrl(asset.getImageUrl())
                            .weightPct(weightPct)
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

    private Map<UUID, Double> calculateAssetReturns(UUID portfolioId, Set<UUID> assetIds) {
        return portfolioReturnCalculator.calculateAssetReturns(portfolioId, assetIds);
    }
}