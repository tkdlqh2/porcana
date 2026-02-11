package com.porcana.domain.portfolio;

import com.porcana.domain.portfolio.command.CreatePortfolioCommand;
import com.porcana.domain.portfolio.command.UpdateAssetWeightsCommand;
import com.porcana.domain.portfolio.command.UpdatePortfolioNameCommand;
import com.porcana.domain.portfolio.dto.*;
import com.porcana.domain.portfolio.service.PortfolioService;
import com.porcana.global.guest.GuestSessionExtractor;
import com.porcana.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@Tag(name = "Portfolio", description = "포트폴리오 API")
@RestController
@RequestMapping("/api/v1/portfolios")
@RequiredArgsConstructor
@SecurityRequirements({
        @SecurityRequirement(name = "JWT"),
        @SecurityRequirement(name = "GuestSession")
})
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final GuestSessionExtractor guestSessionExtractor;

    @Operation(
            summary = "포트폴리오 목록 조회",
            description = "사용자 또는 게스트의 모든 포트폴리오를 조회합니다. 생성일 기준 내림차순으로 정렬됩니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    @GetMapping
    public ResponseEntity<List<PortfolioListResponse>> getPortfolios(HttpServletRequest request) {
        UUID userId = extractUserId();
        UUID guestSessionId = guestSessionExtractor.extractGuestSessionId(request);

        List<PortfolioListResponse> response = portfolioService.getPortfolios(userId, guestSessionId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "포트폴리오 생성",
            description = "새로운 포트폴리오를 생성합니다. 비회원도 생성 가능합니다 (최대 3개). 초기 상태는 DRAFT입니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "생성 성공"),
                    @ApiResponse(responseCode = "400", description = "잘못된 요청 또는 게스트 포트폴리오 개수 초과", content = @Content)
            }
    )
    @PostMapping
    public ResponseEntity<CreatePortfolioResponse> createPortfolio(
            @Valid @RequestBody CreatePortfolioRequest request,
            HttpServletRequest httpRequest) {
        UUID userId = extractUserId();
        UUID guestSessionId = guestSessionExtractor.extractGuestSessionId(httpRequest);

        CreatePortfolioCommand command = CreatePortfolioCommand.from(request, userId);
        CreatePortfolioResponse response = portfolioService.createPortfolio(command, userId, guestSessionId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "포트폴리오 상세 조회",
            description = "포트폴리오의 상세 정보를 조회합니다. 포지션 정보를 포함합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "400", description = "포트폴리오를 찾을 수 없거나 권한이 없음", content = @Content)
            }
    )
    @GetMapping("/{portfolioId}")
    public ResponseEntity<PortfolioDetailResponse> getPortfolio(
            @Parameter(description = "포트폴리오 ID", required = true) @PathVariable UUID portfolioId,
            HttpServletRequest request) {
        UUID userId = extractUserId();
        UUID guestSessionId = guestSessionExtractor.extractGuestSessionId(request);

        PortfolioDetailResponse response = portfolioService.getPortfolio(portfolioId, userId, guestSessionId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "포트폴리오 성과 차트 조회",
            description = "지정한 기간의 포트폴리오 성과 차트를 조회합니다. 1M, 3M, 1Y 중 선택 가능합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "400", description = "포트폴리오를 찾을 수 없거나 권한이 없음", content = @Content)
            }
    )
    @GetMapping("/{portfolioId}/performance")
    public ResponseEntity<PortfolioPerformanceResponse> getPortfolioPerformance(
            @Parameter(description = "포트폴리오 ID", required = true) @PathVariable UUID portfolioId,
            @Parameter(description = "조회 기간 (1M, 3M, 1Y)", required = true) @RequestParam String range,
            HttpServletRequest request) {
        UUID userId = extractUserId();
        UUID guestSessionId = guestSessionExtractor.extractGuestSessionId(request);

        PortfolioPerformanceResponse response = portfolioService.getPortfolioPerformance(portfolioId, userId, guestSessionId, range);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "포트폴리오 이름 수정",
            description = "포트폴리오의 이름을 수정합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "수정 성공"),
                    @ApiResponse(responseCode = "400", description = "포트폴리오를 찾을 수 없거나 권한이 없음", content = @Content),
                    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
            }
    )
    @PatchMapping("/{portfolioId}/name")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<UpdatePortfolioNameResponse> updatePortfolioName(
            @Parameter(description = "포트폴리오 ID", required = true) @PathVariable UUID portfolioId,
            @Valid @RequestBody UpdatePortfolioNameRequest request,
            @CurrentUser UUID userId) {
        UpdatePortfolioNameCommand command =
                UpdatePortfolioNameCommand.from(request, portfolioId, userId);
        UpdatePortfolioNameResponse response = portfolioService.updatePortfolioName(command);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "자산 비중 수정",
            description = "포트폴리오 내 자산들의 비중을 일괄 수정합니다. 비중의 합계는 반드시 100%가 되어야 합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "수정 성공"),
                    @ApiResponse(responseCode = "400", description = "포트폴리오를 찾을 수 없거나 비중 합계가 100%가 아님", content = @Content),
                    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
            }
    )
    @PutMapping("/{portfolioId}/weights")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<UpdateAssetWeightsResponse> updateAssetWeights(
            @Parameter(description = "포트폴리오 ID", required = true) @PathVariable UUID portfolioId,
            @Valid @RequestBody UpdateAssetWeightsRequest request,
            @CurrentUser UUID userId) {
        UpdateAssetWeightsCommand command = UpdateAssetWeightsCommand.from(request, portfolioId, userId);
        UpdateAssetWeightsResponse response = portfolioService.updateAssetWeights(command);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "포트폴리오 삭제",
            description = "포트폴리오를 삭제합니다 (soft delete). 삭제된 포트폴리오는 30일 후 자동으로 완전 삭제됩니다. 비회원도 삭제 가능합니다.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "삭제 성공"),
                    @ApiResponse(responseCode = "400", description = "포트폴리오를 찾을 수 없거나 권한이 없음", content = @Content)
            }
    )
    @DeleteMapping("/{portfolioId}")
    public ResponseEntity<Void> deletePortfolio(
            @Parameter(description = "포트폴리오 ID", required = true) @PathVariable UUID portfolioId,
            HttpServletRequest request) {
        UUID userId = extractUserId();
        UUID guestSessionId = guestSessionExtractor.extractGuestSessionId(request);

        portfolioService.deletePortfolio(portfolioId, userId, guestSessionId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Extract user ID from Security Context
     * Returns null if not authenticated
     */
    private UUID extractUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID) {
            return (UUID) principal;
        }

        return null;
    }
}