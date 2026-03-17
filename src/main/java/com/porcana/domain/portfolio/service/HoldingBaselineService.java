package com.porcana.domain.portfolio.service;

import com.porcana.domain.asset.AssetPriceRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.asset.entity.AssetPrice;
import com.porcana.domain.exchangerate.ExchangeRateRepository;
import com.porcana.domain.exchangerate.entity.CurrencyCode;
import com.porcana.domain.exchangerate.entity.ExchangeRate;
import com.porcana.domain.portfolio.dto.baseline.*;
import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioAsset;
import com.porcana.domain.portfolio.entity.PortfolioHoldingBaseline;
import com.porcana.domain.portfolio.entity.PortfolioHoldingBaselineItem;
import com.porcana.domain.portfolio.repository.PortfolioAssetRepository;
import com.porcana.domain.portfolio.repository.PortfolioHoldingBaselineRepository;
import com.porcana.domain.portfolio.repository.PortfolioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HoldingBaselineService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final PortfolioHoldingBaselineRepository baselineRepository;
    private final AssetRepository assetRepository;
    private final AssetPriceRepository assetPriceRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    /**
     * 시드 금액으로 Baseline 설정
     * 포트폴리오 비중과 현재가를 기반으로 각 종목별 수량 자동 계산
     */
    @Transactional
    public BaselineResponse setSeed(UUID portfolioId, UUID userId, SetSeedRequest request) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("포트폴리오를 찾을 수 없습니다: " + portfolioId));

        // 포트폴리오 소유자 확인
        validateOwnership(portfolio, userId);

        // 포트폴리오 자산 조회
        List<PortfolioAsset> portfolioAssets = portfolioAssetRepository.findByPortfolioId(portfolioId);
        if (portfolioAssets.isEmpty()) {
            throw new IllegalStateException("포트폴리오에 자산이 없습니다.");
        }

        // 자산 정보 조회
        List<UUID> assetIds = portfolioAssets.stream()
                .map(PortfolioAsset::getAssetId)
                .toList();
        Map<UUID, Asset> assetMap = assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, a -> a));

        // 환율 조회 (USD → KRW)
        BigDecimal usdKrw = getLatestExchangeRate();

        // 기준 통화 결정
        PortfolioHoldingBaseline.Currency baseCurrency = parseCurrency(request.baseCurrency());
        BigDecimal seedMoney = request.seedMoney();

        // 입력 통화를 기준 통화로 정규화 (내부 계산은 기준 통화 기준)
        // baseCurrency가 USD면 자산 가격을 USD로, KRW면 KRW로 계산
        boolean isUsdBase = baseCurrency == PortfolioHoldingBaseline.Currency.USD;

        // 각 종목별 수량 계산
        List<CalculatedItem> calculatedItems = new ArrayList<>();
        BigDecimal totalInvested = BigDecimal.ZERO;

        for (PortfolioAsset pa : portfolioAssets) {
            Asset asset = assetMap.get(pa.getAssetId());
            if (asset == null) continue;

            // 현재가 조회
            BigDecimal currentPrice = getLatestPrice(asset);
            if (currentPrice == null || currentPrice.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            // 기준 통화에 맞게 가격 변환
            BigDecimal priceInBaseCurrency;
            if (isUsdBase) {
                // USD 기준: KR 자산은 USD로 변환, US 자산은 그대로
                priceInBaseCurrency = asset.getMarket() == Asset.Market.KR
                        ? currentPrice.divide(usdKrw, 4, RoundingMode.HALF_UP)
                        : currentPrice;
            } else {
                // KRW 기준: US 자산은 KRW로 변환, KR 자산은 그대로
                priceInBaseCurrency = asset.getMarket() == Asset.Market.US
                        ? currentPrice.multiply(usdKrw)
                        : currentPrice;
            }

            // 목표 금액 = 시드 × 비중 (기준 통화)
            BigDecimal targetAmount = seedMoney.multiply(pa.getWeightPct()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            // 수량 = 목표금액 / 현재가 (정수로 버림)
            int quantity = targetAmount.divide(priceInBaseCurrency, 0, RoundingMode.DOWN).intValue();

            // 실제 투자 금액 (기준 통화)
            BigDecimal actualAmount = priceInBaseCurrency.multiply(BigDecimal.valueOf(quantity));
            totalInvested = totalInvested.add(actualAmount);

            calculatedItems.add(new CalculatedItem(
                    asset,
                    pa.getWeightPct(),
                    BigDecimal.valueOf(quantity),
                    currentPrice,
                    priceInBaseCurrency
            ));
        }

        // 잔여 현금
        BigDecimal cashAmount = seedMoney.subtract(totalInvested);

        // 기존 Baseline 삭제 후 새로 생성
        baselineRepository.findByPortfolioId(portfolioId)
                .ifPresent(baselineRepository::delete);

        PortfolioHoldingBaseline baseline = PortfolioHoldingBaseline.create(
                portfolioId,
                userId,
                PortfolioHoldingBaseline.SourceType.SEEDED,
                baseCurrency,
                cashAmount,
                "시드 금액: " + seedMoney + " " + baseCurrency.name()
        );

        // 아이템 추가
        for (CalculatedItem item : calculatedItems) {
            baseline.addItem(
                    item.asset.getId(),
                    item.quantity,
                    item.currentPrice,  // 현재가를 평균 매수가로 저장
                    item.targetWeightPct
            );
        }

        PortfolioHoldingBaseline saved = baselineRepository.save(baseline);

        return buildBaselineResponse(saved, calculatedItems, seedMoney);
    }

    /**
     * Baseline 조회
     */
    @Transactional(readOnly = true)
    public BaselineResponse getBaseline(UUID portfolioId, UUID userId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("포트폴리오를 찾을 수 없습니다: " + portfolioId));

        validateOwnership(portfolio, userId);

        Optional<PortfolioHoldingBaseline> baselineOpt = baselineRepository.findByPortfolioIdWithItems(portfolioId);

        if (baselineOpt.isEmpty()) {
            return BaselineResponse.notExists();
        }

        PortfolioHoldingBaseline baseline = baselineOpt.get();
        BigDecimal usdKrw = getLatestExchangeRate();
        PortfolioHoldingBaseline.Currency baseCurrency = baseline.getBaseCurrency();

        // 자산 정보 조회
        List<UUID> assetIds = baseline.getItems().stream()
                .map(PortfolioHoldingBaselineItem::getAssetId)
                .toList();
        Map<UUID, Asset> assetMap = assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, a -> a));

        // 응답 생성 (기준 통화 기준)
        List<BaselineResponse.ItemResponse> itemResponses = new ArrayList<>();
        BigDecimal cashAmount = baseline.getCashAmount() != null ? baseline.getCashAmount() : BigDecimal.ZERO;
        BigDecimal totalValue = cashAmount;
        BigDecimal seedMoney = cashAmount;  // 원본 시드 = avgPrice * quantity 합계 + cashAmount

        for (PortfolioHoldingBaselineItem item : baseline.getItems()) {
            Asset asset = assetMap.get(item.getAssetId());
            if (asset == null) continue;

            BigDecimal currentPrice = getLatestPrice(asset);
            BigDecimal priceInBaseCurrency = convertPriceToBaseCurrency(asset, currentPrice, baseCurrency, usdKrw);
            BigDecimal currentValue = priceInBaseCurrency.multiply(item.getQuantity());
            totalValue = totalValue.add(currentValue);

            // 원본 시드 복원: avgPrice를 기준 통화로 변환 후 quantity 곱함
            BigDecimal avgPriceInBaseCurrency = convertPriceToBaseCurrency(asset, item.getAvgPrice(), baseCurrency, usdKrw);
            seedMoney = seedMoney.add(avgPriceInBaseCurrency.multiply(item.getQuantity()));

            itemResponses.add(new BaselineResponse.ItemResponse(
                    asset.getId(),
                    asset.getSymbol(),
                    asset.getName(),
                    asset.getMarket().name(),
                    item.getQuantity(),
                    item.getAvgPrice(),
                    item.getTargetWeightPct(),
                    currentPrice,
                    currentValue
            ));
        }

        return BaselineResponse.from(baseline, itemResponses, seedMoney, totalValue);
    }

    /**
     * 추가 입금 추천 (BUY only)
     * 현재 보유 상태에서 추가 자금으로 부족한 비중 자산 매수 추천
     */
    @Transactional(readOnly = true)
    public TopUpPlanResponse getTopUpPlan(UUID portfolioId, UUID userId, TopUpPlanRequest request) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("포트폴리오를 찾을 수 없습니다: " + portfolioId));

        validateOwnership(portfolio, userId);

        PortfolioHoldingBaseline baseline = baselineRepository.findByPortfolioIdWithItems(portfolioId)
                .orElseThrow(() -> new IllegalStateException("Baseline이 설정되지 않았습니다. 먼저 시드 금액을 설정해주세요."));

        BigDecimal usdKrw = getLatestExchangeRate();
        BigDecimal additionalCash = request.additionalCash();
        PortfolioHoldingBaseline.Currency baseCurrency = baseline.getBaseCurrency();

        // 현재 보유 자산 정보
        List<UUID> assetIds = baseline.getItems().stream()
                .map(PortfolioHoldingBaselineItem::getAssetId)
                .toList();
        Map<UUID, Asset> assetMap = assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, a -> a));

        // 현재 총 가치 계산 (기준 통화 기준)
        BigDecimal currentTotalValue = baseline.getCashAmount() != null ? baseline.getCashAmount() : BigDecimal.ZERO;
        Map<UUID, BigDecimal> currentValues = new HashMap<>();

        for (PortfolioHoldingBaselineItem item : baseline.getItems()) {
            Asset asset = assetMap.get(item.getAssetId());
            if (asset == null) continue;

            BigDecimal currentPrice = getLatestPrice(asset);
            BigDecimal priceInBaseCurrency = convertPriceToBaseCurrency(asset, currentPrice, baseCurrency, usdKrw);
            BigDecimal value = priceInBaseCurrency.multiply(item.getQuantity());
            currentValues.put(item.getAssetId(), value);
            currentTotalValue = currentTotalValue.add(value);
        }

        final BigDecimal finalCurrentTotalValue = currentTotalValue;
        BigDecimal newTotalValue = currentTotalValue.add(additionalCash);

        // 비중 괴리 계산 및 매수 추천
        List<TopUpPlanResponse.RecommendationItem> recommendations = new ArrayList<>();

        // 비중 부족 순으로 정렬
        List<PortfolioHoldingBaselineItem> sortedItems = baseline.getItems().stream()
                .sorted((a, b) -> {
                    BigDecimal aCurrentWeight = currentValues.getOrDefault(a.getAssetId(), BigDecimal.ZERO)
                            .divide(finalCurrentTotalValue, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));
                    BigDecimal bCurrentWeight = currentValues.getOrDefault(b.getAssetId(), BigDecimal.ZERO)
                            .divide(finalCurrentTotalValue, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));

                    BigDecimal aDiff = a.getTargetWeightPct().subtract(aCurrentWeight);
                    BigDecimal bDiff = b.getTargetWeightPct().subtract(bCurrentWeight);
                    return bDiff.compareTo(aDiff);  // 부족한 비중이 큰 순서
                })
                .toList();

        BigDecimal remainingCash = additionalCash;

        for (PortfolioHoldingBaselineItem item : sortedItems) {
            if (remainingCash.compareTo(BigDecimal.ZERO) <= 0) break;

            Asset asset = assetMap.get(item.getAssetId());
            if (asset == null) continue;

            BigDecimal currentPrice = getLatestPrice(asset);
            BigDecimal priceInBaseCurrency = convertPriceToBaseCurrency(asset, currentPrice, baseCurrency, usdKrw);
            if (priceInBaseCurrency == null || priceInBaseCurrency.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal currentValue = currentValues.getOrDefault(item.getAssetId(), BigDecimal.ZERO);
            BigDecimal currentWeight = currentTotalValue.compareTo(BigDecimal.ZERO) > 0
                    ? currentValue.divide(currentTotalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            BigDecimal targetWeight = item.getTargetWeightPct();
            BigDecimal weightDiff = targetWeight.subtract(currentWeight);

            // 비중이 부족한 경우만 추천
            if (weightDiff.compareTo(BigDecimal.ZERO) <= 0) continue;

            // 목표 금액까지 채우려면 필요한 금액
            BigDecimal targetValue = newTotalValue.multiply(targetWeight).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal neededAmount = targetValue.subtract(currentValue);

            // 사용 가능한 금액 내에서 매수
            BigDecimal buyAmount = neededAmount.min(remainingCash);
            int quantity = buyAmount.divide(priceInBaseCurrency, 0, RoundingMode.DOWN).intValue();

            if (quantity <= 0) continue;

            BigDecimal actualBuyAmount = priceInBaseCurrency.multiply(BigDecimal.valueOf(quantity));
            remainingCash = remainingCash.subtract(actualBuyAmount);

            BigDecimal newValue = currentValue.add(actualBuyAmount);
            BigDecimal weightAfterBuy = newValue.divide(newTotalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

            recommendations.add(new TopUpPlanResponse.RecommendationItem(
                    asset.getId(),
                    asset.getSymbol(),
                    asset.getName(),
                    asset.getMarket().name(),
                    targetWeight,
                    currentWeight.setScale(2, RoundingMode.HALF_UP),
                    weightAfterBuy.setScale(2, RoundingMode.HALF_UP),
                    currentPrice,
                    quantity,
                    actualBuyAmount,
                    String.format("목표 %.1f%% vs 현재 %.1f%%, 매수 후 %.1f%%",
                            targetWeight, currentWeight, weightAfterBuy)
            ));
        }

        return new TopUpPlanResponse(
                portfolioId,
                additionalCash,
                baseline.getBaseCurrency().name(),
                currentTotalValue,
                newTotalValue,
                recommendations,
                remainingCash
        );
    }

    /**
     * 리밸런싱 상태 조회
     * 현재 보유 상태와 목표 비중의 괴리 확인
     */
    @Transactional(readOnly = true)
    public RebalanceStatusResponse getRebalanceStatus(UUID portfolioId, UUID userId, BigDecimal thresholdPct) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("포트폴리오를 찾을 수 없습니다: " + portfolioId));

        validateOwnership(portfolio, userId);

        Optional<PortfolioHoldingBaseline> baselineOpt = baselineRepository.findByPortfolioIdWithItems(portfolioId);
        if (baselineOpt.isEmpty()) {
            return RebalanceStatusResponse.noBaseline(portfolioId);
        }

        PortfolioHoldingBaseline baseline = baselineOpt.get();
        BigDecimal usdKrw = getLatestExchangeRate();
        BigDecimal threshold = thresholdPct != null ? thresholdPct : new BigDecimal("5.0");
        PortfolioHoldingBaseline.Currency baseCurrency = baseline.getBaseCurrency();

        // 자산 정보 조회
        List<UUID> assetIds = baseline.getItems().stream()
                .map(PortfolioHoldingBaselineItem::getAssetId)
                .toList();
        Map<UUID, Asset> assetMap = assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, a -> a));

        // 현재 총 가치 및 개별 자산 가치 계산 (기준 통화 기준)
        BigDecimal totalValue = baseline.getCashAmount() != null ? baseline.getCashAmount() : BigDecimal.ZERO;
        Map<UUID, AssetValueInfo> assetValues = new HashMap<>();

        for (PortfolioHoldingBaselineItem item : baseline.getItems()) {
            Asset asset = assetMap.get(item.getAssetId());
            if (asset == null) continue;

            BigDecimal currentPrice = getLatestPrice(asset);
            BigDecimal priceInBaseCurrency = convertPriceToBaseCurrency(asset, currentPrice, baseCurrency, usdKrw);
            BigDecimal valueInBaseCurrency = priceInBaseCurrency.multiply(item.getQuantity());
            totalValue = totalValue.add(valueInBaseCurrency);

            assetValues.put(item.getAssetId(), new AssetValueInfo(
                    asset, item, currentPrice, priceInBaseCurrency, valueInBaseCurrency
            ));
        }

        // 괴리도 계산
        List<RebalanceStatusResponse.ItemStatus> itemStatuses = new ArrayList<>();
        BigDecimal maxDeviation = BigDecimal.ZERO;
        boolean needsRebalancing = false;

        final BigDecimal finalTotalValue = totalValue;
        for (PortfolioHoldingBaselineItem item : baseline.getItems()) {
            AssetValueInfo valueInfo = assetValues.get(item.getAssetId());
            if (valueInfo == null) continue;

            BigDecimal targetWeight = item.getTargetWeightPct();
            BigDecimal currentWeight = finalTotalValue.compareTo(BigDecimal.ZERO) > 0
                    ? valueInfo.valueInBaseCurrency.divide(finalTotalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            BigDecimal deviation = currentWeight.subtract(targetWeight);
            BigDecimal absDeviation = deviation.abs();

            if (absDeviation.compareTo(maxDeviation) > 0) {
                maxDeviation = absDeviation;
            }
            if (absDeviation.compareTo(threshold) > 0) {
                needsRebalancing = true;
            }

            String action = deviation.compareTo(threshold) > 0 ? "SELL"
                    : deviation.compareTo(threshold.negate()) < 0 ? "BUY"
                    : "HOLD";

            itemStatuses.add(new RebalanceStatusResponse.ItemStatus(
                    valueInfo.asset.getId(),
                    valueInfo.asset.getSymbol(),
                    valueInfo.asset.getName(),
                    valueInfo.asset.getMarket().name(),
                    targetWeight.setScale(2, RoundingMode.HALF_UP),
                    currentWeight.setScale(2, RoundingMode.HALF_UP),
                    deviation.setScale(2, RoundingMode.HALF_UP),
                    action,
                    valueInfo.item.getQuantity().setScale(0, RoundingMode.DOWN).intValue(),
                    valueInfo.currentPrice,
                    valueInfo.valueInBaseCurrency.setScale(0, RoundingMode.HALF_UP)
            ));
        }

        return new RebalanceStatusResponse(
                portfolioId,
                true,
                needsRebalancing,
                java.time.LocalDateTime.now(),
                threshold,
                new RebalanceStatusResponse.Summary(
                        totalValue.setScale(0, RoundingMode.HALF_UP),
                        baseline.getCashAmount(),
                        maxDeviation.setScale(2, RoundingMode.HALF_UP)
                ),
                itemStatuses
        );
    }

    /**
     * 전체 리밸런싱 플랜
     * BUY + SELL 모두 포함
     */
    @Transactional(readOnly = true)
    public RebalancingPlanResponse getRebalancingPlan(UUID portfolioId, UUID userId, RebalancingPlanRequest request) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("포트폴리오를 찾을 수 없습니다: " + portfolioId));

        validateOwnership(portfolio, userId);

        Optional<PortfolioHoldingBaseline> baselineOpt = baselineRepository.findByPortfolioIdWithItems(portfolioId);
        if (baselineOpt.isEmpty()) {
            return RebalancingPlanResponse.noBaseline(portfolioId);
        }

        PortfolioHoldingBaseline baseline = baselineOpt.get();
        BigDecimal usdKrw = getLatestExchangeRate();
        BigDecimal threshold = request.thresholdPct() != null ? request.thresholdPct() : new BigDecimal("5.0");
        PortfolioHoldingBaseline.Currency baseCurrency = baseline.getBaseCurrency();

        // 자산 정보 조회
        List<UUID> assetIds = baseline.getItems().stream()
                .map(PortfolioHoldingBaselineItem::getAssetId)
                .toList();
        Map<UUID, Asset> assetMap = assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, a -> a));

        // 현재 총 가치 및 개별 자산 가치 계산 (기준 통화 기준)
        BigDecimal totalValue = baseline.getCashAmount() != null ? baseline.getCashAmount() : BigDecimal.ZERO;
        Map<UUID, AssetValueInfo> assetValues = new HashMap<>();

        for (PortfolioHoldingBaselineItem item : baseline.getItems()) {
            Asset asset = assetMap.get(item.getAssetId());
            if (asset == null) continue;

            BigDecimal currentPrice = getLatestPrice(asset);
            BigDecimal priceInBaseCurrency = convertPriceToBaseCurrency(asset, currentPrice, baseCurrency, usdKrw);
            BigDecimal valueInBaseCurrency = priceInBaseCurrency.multiply(item.getQuantity());
            totalValue = totalValue.add(valueInBaseCurrency);

            assetValues.put(item.getAssetId(), new AssetValueInfo(
                    asset, item, currentPrice, priceInBaseCurrency, valueInBaseCurrency
            ));
        }

        // 리밸런싱 액션 계산
        List<RebalancingPlanResponse.ActionItem> actions = new ArrayList<>();
        BigDecimal totalBuyAmount = BigDecimal.ZERO;
        BigDecimal totalSellAmount = BigDecimal.ZERO;
        boolean needsRebalancing = false;

        final BigDecimal finalTotalValue = totalValue;
        for (PortfolioHoldingBaselineItem item : baseline.getItems()) {
            AssetValueInfo valueInfo = assetValues.get(item.getAssetId());
            if (valueInfo == null) continue;

            BigDecimal targetWeight = item.getTargetWeightPct();
            BigDecimal currentWeight = finalTotalValue.compareTo(BigDecimal.ZERO) > 0
                    ? valueInfo.valueInBaseCurrency.divide(finalTotalValue, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;
            BigDecimal deviation = currentWeight.subtract(targetWeight);
            BigDecimal absDeviation = deviation.abs();

            // 임계값 이하면 스킵
            if (absDeviation.compareTo(threshold) <= 0) continue;

            needsRebalancing = true;

            // 목표 금액 계산 (기준 통화)
            BigDecimal targetValue = finalTotalValue.multiply(targetWeight).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal diff = targetValue.subtract(valueInfo.valueInBaseCurrency);

            String action;
            int actionQuantity;
            BigDecimal actionAmount;

            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                // BUY
                action = "BUY";
                actionQuantity = diff.divide(valueInfo.priceInBaseCurrency, 0, RoundingMode.DOWN).intValue();
                actionAmount = valueInfo.priceInBaseCurrency.multiply(BigDecimal.valueOf(actionQuantity));
                totalBuyAmount = totalBuyAmount.add(actionAmount);
            } else {
                // SELL
                action = "SELL";
                actionQuantity = diff.abs().divide(valueInfo.priceInBaseCurrency, 0, RoundingMode.DOWN).intValue();
                actionAmount = valueInfo.priceInBaseCurrency.multiply(BigDecimal.valueOf(actionQuantity));
                totalSellAmount = totalSellAmount.add(actionAmount);
            }

            if (actionQuantity == 0) continue;

            int currentQuantity = valueInfo.item.getQuantity().setScale(0, RoundingMode.DOWN).intValue();
            int afterQuantity = action.equals("BUY")
                    ? currentQuantity + actionQuantity
                    : currentQuantity - actionQuantity;

            actions.add(new RebalancingPlanResponse.ActionItem(
                    valueInfo.asset.getId(),
                    valueInfo.asset.getSymbol(),
                    valueInfo.asset.getName(),
                    valueInfo.asset.getMarket().name(),
                    action,
                    targetWeight.setScale(2, RoundingMode.HALF_UP),
                    currentWeight.setScale(2, RoundingMode.HALF_UP),
                    deviation.setScale(2, RoundingMode.HALF_UP),
                    currentQuantity,
                    actionQuantity,
                    afterQuantity,
                    valueInfo.currentPrice,
                    actionAmount.setScale(0, RoundingMode.HALF_UP)
            ));
        }

        if (!needsRebalancing) {
            return RebalancingPlanResponse.noRebalancingNeeded(portfolioId, baseline.getId(), threshold, totalValue);
        }

        BigDecimal netCashFlow = totalSellAmount.subtract(totalBuyAmount);
        BigDecimal cashAfterRebalance = (baseline.getCashAmount() != null ? baseline.getCashAmount() : BigDecimal.ZERO)
                .add(netCashFlow);

        return new RebalancingPlanResponse(
                portfolioId,
                baseline.getId(),
                true,
                threshold,
                new RebalancingPlanResponse.Summary(
                        totalValue.setScale(0, RoundingMode.HALF_UP),
                        totalBuyAmount.setScale(0, RoundingMode.HALF_UP),
                        totalSellAmount.setScale(0, RoundingMode.HALF_UP),
                        netCashFlow.setScale(0, RoundingMode.HALF_UP),
                        cashAfterRebalance.setScale(0, RoundingMode.HALF_UP)
                ),
                actions
        );
    }

    // === Private Helper Methods ===

    private record AssetValueInfo(
            Asset asset,
            PortfolioHoldingBaselineItem item,
            BigDecimal currentPrice,
            BigDecimal priceInBaseCurrency,
            BigDecimal valueInBaseCurrency
    ) {}

    /**
     * 자산 가격을 기준 통화로 변환
     */
    private BigDecimal convertPriceToBaseCurrency(Asset asset, BigDecimal currentPrice,
                                                   PortfolioHoldingBaseline.Currency baseCurrency,
                                                   BigDecimal usdKrw) {
        boolean isUsdBase = baseCurrency == PortfolioHoldingBaseline.Currency.USD;

        if (isUsdBase) {
            // USD 기준: KR 자산은 USD로 변환, US 자산은 그대로
            return asset.getMarket() == Asset.Market.KR
                    ? currentPrice.divide(usdKrw, 4, RoundingMode.HALF_UP)
                    : currentPrice;
        } else {
            // KRW 기준: US 자산은 KRW로 변환, KR 자산은 그대로
            return asset.getMarket() == Asset.Market.US
                    ? currentPrice.multiply(usdKrw)
                    : currentPrice;
        }
    }

    private void validateOwnership(Portfolio portfolio, UUID userId) {
        if (userId != null && !userId.equals(portfolio.getUserId())) {
            throw new IllegalArgumentException("포트폴리오 접근 권한이 없습니다.");
        }
    }

    private BigDecimal getLatestExchangeRate() {
        return exchangeRateRepository.findTopByCurrencyCodeOrderByExchangeDateDesc(CurrencyCode.USD)
                .map(ExchangeRate::getBaseRate)
                .orElse(BigDecimal.valueOf(1350));  // 기본값
    }

    private BigDecimal getLatestPrice(Asset asset) {
        return assetPriceRepository.findFirstByAssetOrderByPriceDateDesc(asset)
                .map(AssetPrice::getClosePrice)
                .orElse(BigDecimal.ZERO);
    }

    private PortfolioHoldingBaseline.Currency parseCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return PortfolioHoldingBaseline.Currency.KRW;
        }
        try {
            return PortfolioHoldingBaseline.Currency.valueOf(currency.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PortfolioHoldingBaseline.Currency.KRW;
        }
    }

    private BaselineResponse buildBaselineResponse(PortfolioHoldingBaseline baseline,
                                                    List<CalculatedItem> items,
                                                    BigDecimal seedMoney) {
        BigDecimal totalValue = baseline.getCashAmount() != null ? baseline.getCashAmount() : BigDecimal.ZERO;

        List<BaselineResponse.ItemResponse> itemResponses = items.stream()
                .map(item -> new BaselineResponse.ItemResponse(
                        item.asset.getId(),
                        item.asset.getSymbol(),
                        item.asset.getName(),
                        item.asset.getMarket().name(),
                        item.quantity,
                        item.currentPrice,
                        item.targetWeightPct,
                        item.currentPrice,
                        item.priceInBaseCurrency.multiply(item.quantity)
                ))
                .toList();

        // totalValue = 모든 아이템의 현재 평가금액 합계 + 현금
        for (BaselineResponse.ItemResponse item : itemResponses) {
            totalValue = totalValue.add(item.currentValue());
        }

        return BaselineResponse.from(baseline, itemResponses, seedMoney, totalValue);
    }

    private record CalculatedItem(
            Asset asset,
            BigDecimal targetWeightPct,
            BigDecimal quantity,
            BigDecimal currentPrice,
            BigDecimal priceInBaseCurrency
    ) {}
}
