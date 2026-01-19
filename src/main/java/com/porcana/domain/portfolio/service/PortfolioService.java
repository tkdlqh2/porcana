package com.porcana.domain.portfolio.service;

import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.portfolio.command.CreatePortfolioCommand;
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

    public List<PortfolioListResponse> getPortfolios(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Portfolio> portfolios = portfolioRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return portfolios.stream()
                .map(portfolio -> {
                    Double totalReturnPct = calculateTotalReturn(portfolio.getId());
                    boolean isMain = portfolio.getId().equals(user.getMainPortfolioId());

                    return PortfolioListResponse.builder()
                            .portfolioId(portfolio.getId().toString())
                            .name(portfolio.getName())
                            .status(portfolio.getStatus().name())
                            .isMain(isMain)
                            .totalReturnPct(totalReturnPct)
                            .createdAt(portfolio.getCreatedAt().toLocalDate())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public CreatePortfolioResponse createPortfolio(CreatePortfolioCommand command) {
        Portfolio portfolio = Portfolio.builder()
                .userId(command.getUserId())
                .name(command.getName())
                .status(PortfolioStatus.DRAFT)
                .build();

        Portfolio saved = portfolioRepository.save(portfolio);

        return CreatePortfolioResponse.builder()
                .portfolioId(saved.getId().toString())
                .name(saved.getName())
                .status(saved.getStatus().name())
                .createdAt(saved.getCreatedAt().toLocalDate())
                .build();
    }

    public PortfolioDetailResponse getPortfolio(UUID portfolioId, UUID userId) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found or access denied"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean isMain = portfolio.getId().equals(user.getMainPortfolioId());
        Double totalReturnPct = calculateTotalReturn(portfolio.getId());
        List<PortfolioDetailResponse.PositionInfo> positions = buildPositions(portfolio.getId());

        return PortfolioDetailResponse.builder()
                .portfolioId(portfolio.getId().toString())
                .name(portfolio.getName())
                .status(portfolio.getStatus().name())
                .isMain(isMain)
                .startedAt(portfolio.getStartedAt())
                .totalReturnPct(totalReturnPct)
                .positions(positions)
                .build();
    }

    @Transactional
    public StartPortfolioResponse startPortfolio(UUID portfolioId, UUID userId) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found or access denied"));

        if (portfolio.getStatus() != PortfolioStatus.DRAFT) {
            throw new IllegalStateException("Portfolio is already started");
        }

        portfolio.start();
        Portfolio saved = portfolioRepository.save(portfolio);

        return StartPortfolioResponse.builder()
                .portfolioId(saved.getId().toString())
                .status(saved.getStatus().name())
                .startedAt(saved.getStartedAt())
                .build();
    }

    public PortfolioPerformanceResponse getPortfolioPerformance(UUID portfolioId, UUID userId, String range) {
        Portfolio portfolio = portfolioRepository.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found or access denied"));

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = calculateStartDate(endDate, range);

        List<PortfolioDailyReturn> returns = portfolioDailyReturnRepository
                .findByPortfolioIdAndReturnDateBetweenOrderByReturnDateAsc(portfolioId, startDate, endDate);

        List<PortfolioPerformanceResponse.PerformancePoint> points = new ArrayList<>();

        // Start with 100 at the start date
        double cumulativeValue = 100.0;
        points.add(PortfolioPerformanceResponse.PerformancePoint.builder()
                .date(portfolio.getStartedAt() != null ? portfolio.getStartedAt() : startDate)
                .value(100.0)
                .build());

        // Calculate cumulative values
        for (PortfolioDailyReturn dailyReturn : returns) {
            double dailyReturnValue = dailyReturn.getReturnTotal().doubleValue() / 100.0;
            cumulativeValue *= (1.0 + dailyReturnValue);

            points.add(PortfolioPerformanceResponse.PerformancePoint.builder()
                    .date(dailyReturn.getReturnDate())
                    .value(cumulativeValue)
                    .build());
        }

        return PortfolioPerformanceResponse.builder()
                .portfolioId(portfolio.getId().toString())
                .range(range)
                .points(points)
                .build();
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
                            .weightPct(pa.getWeightPct().doubleValue())
                            .returnPct(returnPct)
                            .build();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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