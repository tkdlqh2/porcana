package com.porcana.domain.portfolio.service;

import com.porcana.domain.portfolio.entity.PortfolioDailyReturn;
import com.porcana.domain.portfolio.entity.SnapshotAssetDailyReturn;
import com.porcana.domain.portfolio.repository.PortfolioDailyReturnRepository;
import com.porcana.domain.portfolio.repository.SnapshotAssetDailyReturnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 포트폴리오 및 자산별 수익률 계산을 담당하는 컴포넌트
 * HomeService와 PortfolioService에서 공통으로 사용
 */
@Component
@RequiredArgsConstructor
public class PortfolioReturnCalculator {

    private final PortfolioDailyReturnRepository portfolioDailyReturnRepository;
    private final SnapshotAssetDailyReturnRepository snapshotAssetDailyReturnRepository;

    /**
     * 포트폴리오 전체 누적 수익률 계산
     *
     * @param portfolioId 포트폴리오 ID
     * @return 누적 수익률 (%)
     */
    public Double calculateTotalReturn(UUID portfolioId) {
        List<PortfolioDailyReturn> returns = portfolioDailyReturnRepository
                .findByPortfolioIdOrderByReturnDateAsc(portfolioId);

        if (returns.isEmpty()) {
            return 0.0;
        }

        // Calculate cumulative return: (1 + r1) * (1 + r2) * ... - 1
        double cumulativeReturn = 1.0;
        for (PortfolioDailyReturn dailyReturn : returns) {
            double dailyReturnValue = dailyReturn.getReturnTotal().doubleValue() / 100.0;
            cumulativeReturn *= (1.0 + dailyReturnValue);
        }

        return (cumulativeReturn - 1.0) * 100.0;
    }

    /**
     * 자산별 누적 수익률 계산
     *
     * @param portfolioId 포트폴리오 ID
     * @param assetIds    자산 ID 목록
     * @return 자산 ID -> 누적 수익률(%) 맵
     */
    public Map<UUID, Double> calculateAssetReturns(UUID portfolioId, Set<UUID> assetIds) {
        // 해당 포트폴리오의 모든 자산별 일일 수익률 조회
        List<SnapshotAssetDailyReturn> assetReturns = snapshotAssetDailyReturnRepository
                .findByPortfolioIdOrderByReturnDateAsc(portfolioId);

        if (assetReturns.isEmpty()) {
            // 데이터가 없으면 모두 0.0으로 반환
            return assetIds.stream()
                    .collect(Collectors.toMap(assetId -> assetId, assetId -> 0.0));
        }

        // 자산별로 그룹핑
        Map<UUID, List<SnapshotAssetDailyReturn>> assetReturnsByAsset = assetReturns.stream()
                .filter(ar -> assetIds.contains(ar.getAssetId()))
                .collect(Collectors.groupingBy(SnapshotAssetDailyReturn::getAssetId));

        // 각 자산별로 누적 수익률 계산
        return assetIds.stream()
                .collect(Collectors.toMap(
                        assetId -> assetId,
                        assetId -> {
                            List<SnapshotAssetDailyReturn> dailyReturns = assetReturnsByAsset.get(assetId);
                            if (dailyReturns == null || dailyReturns.isEmpty()) {
                                return 0.0;
                            }

                            // 누적 수익률 계산: (1 + r1) * (1 + r2) * ... - 1
                            double cumulativeReturn = 1.0;
                            for (SnapshotAssetDailyReturn dailyReturn : dailyReturns) {
                                double dailyReturnValue = dailyReturn.getAssetReturnTotal().doubleValue() / 100.0;
                                cumulativeReturn *= (1.0 + dailyReturnValue);
                            }

                            return (cumulativeReturn - 1.0) * 100.0;
                        }
                ));
    }
}