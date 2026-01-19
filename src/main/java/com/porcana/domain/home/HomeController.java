package com.porcana.domain.home;

import com.porcana.domain.home.dto.HomeResponse;
import com.porcana.domain.home.dto.MainPortfolioIdResponse;
import com.porcana.domain.home.service.HomeService;
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

import java.util.UUID;

@Tag(name = "Home", description = "홈 화면 API (메인 포트폴리오 위젯)")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "JWT")
public class HomeController {

    private final HomeService homeService;

    @Operation(
            summary = "홈 화면 조회",
            description = "메인 포트폴리오가 설정되어 있으면 포트폴리오 정보, 차트, 포지션을 반환합니다. 설정되어 있지 않으면 hasMainPortfolio: false를 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
            }
    )
    @GetMapping("/home")
    public ResponseEntity<HomeResponse> getHome(@CurrentUser UUID userId) {
        HomeResponse response = homeService.getHome(userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "메인 포트폴리오 설정",
            description = "지정한 포트폴리오를 메인 포트폴리오로 설정합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "설정 성공"),
                    @ApiResponse(responseCode = "400", description = "포트폴리오를 찾을 수 없거나 권한이 없음", content = @Content),
                    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
            }
    )
    @PutMapping("/portfolios/{portfolioId}/main")
    public ResponseEntity<MainPortfolioIdResponse> setMainPortfolio(
            @Parameter(description = "포트폴리오 ID", required = true) @PathVariable UUID portfolioId,
            @CurrentUser UUID userId) {
        MainPortfolioIdResponse response = homeService.setMainPortfolio(userId, portfolioId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "메인 포트폴리오 해제",
            description = "메인 포트폴리오 설정을 해제합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "해제 성공"),
                    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
            }
    )
    @DeleteMapping("/portfolios/main")
    public ResponseEntity<MainPortfolioIdResponse> removeMainPortfolio(@CurrentUser UUID userId) {
        MainPortfolioIdResponse response = homeService.removeMainPortfolio(userId);
        return ResponseEntity.ok(response);
    }
}