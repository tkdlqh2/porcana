package com.porcana.domain.home.service;

import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.home.dto.HomeResponse;
import com.porcana.domain.home.dto.MainPortfolioIdResponse;
import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioAsset;
import com.porcana.domain.portfolio.entity.PortfolioDailyReturn;
import com.porcana.domain.portfolio.repository.PortfolioAssetRepository;
import com.porcana.domain.portfolio.repository.PortfolioDailyReturnRepository;
import com.porcana.domain.portfolio.repository.PortfolioRepository;
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

        return portfolioAssets.stream()
                .map(pa -> {
                    Asset asset = assetMap.get(pa.getAssetId());
                    if (asset == null) {
                        return null;
                    }

                    Double returnPct = assetReturns.getOrDefault(pa.getAssetId(), 0.0);

                    return HomeResponse.PositionInfo.builder()
                            .assetId(asset.getId().toString())
                            .ticker(asset.getSymbol())
                            .name(asset.getName())
                            .imageUrl(asset.getImageUrl())
                            .weightPct(pa.getWeightPct().doubleValue())
                            .returnPct(returnPct)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Map<UUID, Double> calculateAssetReturns(UUID portfolioId, Set<UUID> assetIds) {
        return portfolioReturnCalculator.calculateAssetReturns(portfolioId, assetIds);
    }
}