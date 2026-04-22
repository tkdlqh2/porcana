package com.porcana.domain.portfolio.service;

import com.porcana.domain.portfolio.entity.PortfolioDailyReturn;
import com.porcana.domain.portfolio.entity.SnapshotAssetDailyReturn;
import com.porcana.domain.portfolio.repository.PortfolioDailyReturnRepository;
import com.porcana.domain.portfolio.repository.SnapshotAssetDailyReturnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class PortfolioReturnCalculator {

    private final PortfolioDailyReturnRepository portfolioDailyReturnRepository;
    private final SnapshotAssetDailyReturnRepository snapshotAssetDailyReturnRepository;

    /**
     * Calculate compounded portfolio return across snapshots.
     * Each snapshot stores returns relative to its own effective date.
     */
    public Double calculateTotalReturn(UUID portfolioId) {
        List<PortfolioDailyReturn> returns = portfolioDailyReturnRepository
                .findByPortfolioIdOrderByReturnDateAsc(portfolioId);

        if (returns.isEmpty()) {
            return 0.0;
        }

        Map<UUID, Optional<PortfolioDailyReturn>> lastBySnapshot = returns.stream()
                .collect(Collectors.groupingBy(
                        PortfolioDailyReturn::getSnapshotId,
                        Collectors.maxBy(Comparator.comparing(PortfolioDailyReturn::getReturnDate))
                ));

        List<PortfolioDailyReturn> periodReturns = lastBySnapshot.values().stream()
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(PortfolioDailyReturn::getReturnDate))
                .toList();

        double cumulativeReturn = 1.0;
        for (PortfolioDailyReturn periodReturn : periodReturns) {
            double periodReturnValue = periodReturn.getReturnTotal().doubleValue() / 100.0;
            cumulativeReturn *= (1.0 + periodReturnValue);
        }

        return (cumulativeReturn - 1.0) * 100.0;
    }

    /**
     * Build a continuous chart series from snapshot-based cumulative returns.
     * Snapshot changes should not reset the portfolio chart.
     */
    public List<PortfolioValuePoint> calculatePortfolioValueSeries(List<PortfolioDailyReturn> returns, double baseValue) {
        if (returns.isEmpty()) {
            return List.of();
        }

        List<PortfolioDailyReturn> sortedReturns = returns.stream()
                .sorted(Comparator.comparing(PortfolioDailyReturn::getReturnDate))
                .toList();

        double carriedMultiplier = 1.0;
        UUID currentSnapshotId = null;
        double lastSnapshotMultiplier = 1.0;

        java.util.ArrayList<PortfolioValuePoint> points = new java.util.ArrayList<>(sortedReturns.size());
        for (PortfolioDailyReturn dailyReturn : sortedReturns) {
            if (currentSnapshotId != null && !currentSnapshotId.equals(dailyReturn.getSnapshotId())) {
                carriedMultiplier *= lastSnapshotMultiplier;
            }

            currentSnapshotId = dailyReturn.getSnapshotId();
            lastSnapshotMultiplier = 1.0 + dailyReturn.getReturnTotal().doubleValue() / 100.0;

            points.add(new PortfolioValuePoint(
                    dailyReturn.getReturnDate(),
                    baseValue * carriedMultiplier * lastSnapshotMultiplier
            ));
        }

        return points;
    }

    /**
     * Current asset return should reflect the latest snapshot only.
     * After rebalancing, per-asset return resets to the new snapshot baseline.
     */
    public Map<UUID, Double> calculateAssetReturns(UUID portfolioId, Set<UUID> assetIds) {
        List<SnapshotAssetDailyReturn> assetReturns = snapshotAssetDailyReturnRepository
                .findByPortfolioIdOrderByReturnDateAsc(portfolioId);

        if (assetReturns.isEmpty()) {
            return assetIds.stream()
                    .collect(Collectors.toMap(assetId -> assetId, assetId -> 0.0));
        }

        Map<UUID, List<SnapshotAssetDailyReturn>> assetReturnsByAsset = assetReturns.stream()
                .filter(assetReturn -> assetIds.contains(assetReturn.getAssetId()))
                .collect(Collectors.groupingBy(SnapshotAssetDailyReturn::getAssetId));

        return assetIds.stream()
                .collect(Collectors.toMap(
                        assetId -> assetId,
                        assetId -> assetReturnsByAsset.getOrDefault(assetId, List.of()).stream()
                                .max(Comparator.comparing(SnapshotAssetDailyReturn::getReturnDate))
                                .map(snapshotAssetDailyReturn -> snapshotAssetDailyReturn.getAssetReturnTotal().doubleValue())
                                .orElse(0.0)
                ));
    }

    public record PortfolioValuePoint(LocalDate date, double value) {}
}
