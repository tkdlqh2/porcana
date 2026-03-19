package com.porcana.domain.portfolio.controller;

import com.porcana.domain.portfolio.dto.baseline.*;
import com.porcana.domain.portfolio.service.HoldingBaselineService;
import com.porcana.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@Tag(name = "Holding Baseline", description = "포트폴리오 시드 설정 및 리밸런싱 가이드 API")
@SecurityRequirement(name = "JWT")
@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}")
@RequiredArgsConstructor
public class HoldingBaselineController {

    private final HoldingBaselineService holdingBaselineService;

    @Operation(summary = "시드 금액 설정", description = "포트폴리오에 시드 금액을 설정하고 각 종목별 매수 수량을 계산합니다.")
    @PutMapping("/seed")
    public ResponseEntity<BaselineResponse> setSeed(
            @PathVariable UUID portfolioId,
            @Valid @RequestBody SetSeedRequest request,
            @CurrentUser UUID userId) {

        BaselineResponse response = holdingBaselineService.setSeed(portfolioId, userId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Baseline 조회", description = "설정된 시드 금액 및 보유 수량 정보를 조회합니다.")
    @GetMapping("/holding-baseline")
    public ResponseEntity<BaselineResponse> getBaseline(
            @PathVariable UUID portfolioId,
            @CurrentUser UUID userId) {

        BaselineResponse response = holdingBaselineService.getBaseline(portfolioId, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "추가 입금 추천", description = "추가 자금으로 어떤 종목을 매수해야 비중이 맞춰지는지 추천합니다.")
    @PostMapping("/top-up-plan")
    public ResponseEntity<TopUpPlanResponse> getTopUpPlan(
            @PathVariable UUID portfolioId,
            @Valid @RequestBody TopUpPlanRequest request,
            @CurrentUser UUID userId) {

        TopUpPlanResponse response = holdingBaselineService.getTopUpPlan(portfolioId, userId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "추가 입금 실행", description = "실제 매수한 내역을 반영하여 baseline을 업데이트합니다.")
    @PostMapping("/top-up")
    public ResponseEntity<ExecuteTopUpResponse> executeTopUp(
            @PathVariable UUID portfolioId,
            @Valid @RequestBody ExecuteTopUpRequest request,
            @CurrentUser UUID userId) {

        ExecuteTopUpResponse response = holdingBaselineService.executeTopUp(portfolioId, userId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "리밸런싱 상태 조회", description = "현재 보유 상태와 목표 비중의 괴리를 확인합니다.")
    @GetMapping("/rebalance-status")
    public ResponseEntity<RebalanceStatusResponse> getRebalanceStatus(
            @PathVariable UUID portfolioId,
            @RequestParam(required = false, defaultValue = "5.0") BigDecimal thresholdPct,
            @CurrentUser UUID userId) {

        RebalanceStatusResponse response = holdingBaselineService.getRebalanceStatus(portfolioId, userId, thresholdPct);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "리밸런싱 플랜", description = "전체 리밸런싱 플랜을 계산합니다. (BUY + SELL)")
    @PostMapping("/rebalancing-plan")
    public ResponseEntity<RebalancingPlanResponse> getRebalancingPlan(
            @PathVariable UUID portfolioId,
            @Valid @RequestBody RebalancingPlanRequest request,
            @CurrentUser UUID userId) {

        RebalancingPlanResponse response = holdingBaselineService.getRebalancingPlan(portfolioId, userId, request);
        return ResponseEntity.ok(response);
    }
}
