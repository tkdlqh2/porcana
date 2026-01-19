package com.porcana.domain.asset.service;

import com.porcana.domain.asset.AssetPriceRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.dto.AssetChartResponse;
import com.porcana.domain.asset.dto.AssetDetailResponse;
import com.porcana.domain.asset.dto.AssetInMainPortfolioResponse;
import com.porcana.domain.asset.dto.AssetSearchResponse;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetPrice;
import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioAsset;
import com.porcana.domain.portfolio.repository.PortfolioAssetRepository;
import com.porcana.domain.portfolio.repository.PortfolioRepository;
import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetService {

    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final com.porcana.domain.portfolio.service.PortfolioReturnCalculator portfolioReturnCalculator;

    public List<AssetSearchResponse> searchAssets(String query) {
        List<Asset> assets = assetRepository.findByActiveTrue();

        String lowerQuery = query.toLowerCase();

        return assets.stream()
                .filter(asset ->
                        asset.getSymbol().toLowerCase().contains(lowerQuery) ||
                                asset.getName().toLowerCase().contains(lowerQuery)
                )
                .limit(20)
                .map(asset -> AssetSearchResponse.builder()
                        .assetId(asset.getId().toString())
                        .ticker(asset.getSymbol())
                        .name(asset.getName())
                        .exchange(asset.getMarket().name())
                        .country(asset.getMarket() == Asset.Market.KR ? "KR" : "US")
                        .sector(asset.getSector() != null ? asset.getSector().name() : null)
                        .imageUrl(null) // TODO: Add image URL logic
                        .build())
                .collect(Collectors.toList());
    }

    public AssetDetailResponse getAsset(UUID assetId) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        return AssetDetailResponse.builder()
                .assetId(asset.getId().toString())
                .ticker(asset.getSymbol())
                .name(asset.getName())
                .exchange(asset.getMarket().name())
                .country(asset.getMarket() == Asset.Market.KR ? "KR" : "US")
                .sector(asset.getSector() != null ? asset.getSector().name() : null)
                .currency(asset.getMarket() == Asset.Market.KR ? "KRW" : "USD")
                .imageUrl(null) // TODO: Add image URL logic
                .description(null) // TODO: Add description field to Asset entity
                .build();
    }

    public AssetChartResponse getAssetChart(UUID assetId, String range) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found"));

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = calculateStartDate(endDate, range);

        List<AssetPrice> prices = assetPriceRepository
                .findByAssetAndPriceDateBetweenOrderByPriceDateAsc(asset, startDate, endDate);

        List<AssetChartResponse.ChartPoint> points = prices.stream()
                .map(price -> AssetChartResponse.ChartPoint.builder()
                        .date(price.getPriceDate())
                        .price(price.getPrice().doubleValue())
                        .build())
                .collect(Collectors.toList());

        return AssetChartResponse.builder()
                .assetId(asset.getId().toString())
                .range(range)
                .points(points)
                .build();
    }

    public AssetInMainPortfolioResponse isAssetInMainPortfolio(UUID assetId, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UUID mainPortfolioId = user.getMainPortfolioId();

        if (mainPortfolioId == null) {
            return AssetInMainPortfolioResponse.notIncluded();
        }

        Optional<Portfolio> portfolioOpt = portfolioRepository.findById(mainPortfolioId);
        if (portfolioOpt.isEmpty()) {
            return AssetInMainPortfolioResponse.notIncluded();
        }

        Optional<PortfolioAsset> portfolioAssetOpt = portfolioAssetRepository
                .findByPortfolioIdAndAssetId(mainPortfolioId, assetId);

        if (portfolioAssetOpt.isEmpty()) {
            return AssetInMainPortfolioResponse.notIncluded();
        }

        PortfolioAsset portfolioAsset = portfolioAssetOpt.get();

        // Calculate actual return percentage
        Map<UUID, Double> assetReturns = portfolioReturnCalculator.calculateAssetReturns(
                mainPortfolioId, Set.of(assetId));
        Double returnPct = assetReturns.getOrDefault(assetId, 0.0);

        return AssetInMainPortfolioResponse.builder()
                .included(true)
                .portfolioId(mainPortfolioId.toString())
                .weightPct(portfolioAsset.getWeightPct().doubleValue())
                .returnPct(returnPct)
                .build();
    }

    private LocalDate calculateStartDate(LocalDate endDate, String range) {
        return switch (range) {
            case "1M" -> endDate.minusMonths(1);
            case "3M" -> endDate.minusMonths(3);
            case "1Y" -> endDate.minusYears(1);
            default -> endDate.minusMonths(1);
        };
    }
}