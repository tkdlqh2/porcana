package com.porcana.domain.asset;

import com.porcana.domain.asset.dto.AssetChartResponse;
import com.porcana.domain.asset.dto.AssetDetailResponse;
import com.porcana.domain.asset.dto.AssetInMainPortfolioResponse;
import com.porcana.domain.asset.dto.AssetSearchResponse;
import com.porcana.domain.asset.service.AssetService;
import com.porcana.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Asset", description = "자산(종목) API")
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
@SecurityRequirement(name = "JWT")
public class AssetController {

    private final AssetService assetService;

    @Operation(
            summary = "자산 검색",
            description = "종목 코드(ticker) 또는 이름으로 자산을 검색합니다. 최대 20개까지 반환됩니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "검색 성공"),
                    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
            }
    )
    @GetMapping("/search")
    public ResponseEntity<List<AssetSearchResponse>> searchAssets(
            @Parameter(description = "검색어 (ticker 또는 name)", required = true) @RequestParam String query) {
        List<AssetSearchResponse> response = assetService.searchAssets(query);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "자산 상세 조회",
            description = "자산의 상세 정보를 조회합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "400", description = "자산을 찾을 수 없음", content = @Content),
                    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
            }
    )
    @GetMapping("/{assetId}")
    public ResponseEntity<AssetDetailResponse> getAsset(
            @Parameter(description = "자산 ID", required = true) @PathVariable UUID assetId) {
        AssetDetailResponse response = assetService.getAsset(assetId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "자산 차트 조회",
            description = "지정한 기간의 자산 가격 차트를 조회합니다. 1M, 3M, 1Y 중 선택 가능합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "400", description = "자산을 찾을 수 없음", content = @Content),
                    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
            }
    )
    @GetMapping("/{assetId}/chart")
    public ResponseEntity<AssetChartResponse> getAssetChart(
            @Parameter(description = "자산 ID", required = true) @PathVariable UUID assetId,
            @Parameter(description = "조회 기간 (1M, 3M, 1Y)", required = true) @RequestParam String range) {
        AssetChartResponse response = assetService.getAssetChart(assetId, range);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "메인 포트폴리오 포함 여부 확인",
            description = "해당 자산이 사용자의 메인 포트폴리오에 포함되어 있는지 확인합니다. 포함되어 있으면 비중과 수익률 정보를 함께 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
            }
    )
    @GetMapping("/{assetId}/in-my-main-portfolio")
    public ResponseEntity<AssetInMainPortfolioResponse> isAssetInMainPortfolio(
            @Parameter(description = "자산 ID", required = true) @PathVariable UUID assetId,
            @CurrentUser UUID userId) {
        AssetInMainPortfolioResponse response = assetService.isAssetInMainPortfolio(assetId, userId);
        return ResponseEntity.ok(response);
    }
}