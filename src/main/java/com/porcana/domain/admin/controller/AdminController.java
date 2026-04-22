package com.porcana.domain.admin.controller;

import com.porcana.domain.admin.dto.request.CreateAdminRequest;
import com.porcana.domain.admin.dto.request.UpdateAssetDividendRequest;
import com.porcana.domain.admin.dto.request.UpdateAssetImageRequest;
import com.porcana.domain.admin.dto.response.AdminAssetListResponse;
import com.porcana.domain.admin.dto.response.AdminBatchRunDetailResponse;
import com.porcana.domain.admin.dto.response.AdminBatchRunListResponse;
import com.porcana.domain.admin.dto.response.AdminPortfolioDetailResponse;
import com.porcana.domain.admin.dto.response.AdminPortfolioListResponse;
import com.porcana.domain.admin.dto.response.AdminTodayBatchIssueListResponse;
import com.porcana.domain.admin.dto.response.AdminUserDetailResponse;
import com.porcana.domain.admin.dto.response.AdminUserListResponse;
import com.porcana.domain.admin.service.AdminService;
import com.porcana.domain.asset.dto.AssetChartResponse;
import com.porcana.domain.asset.dto.AssetDetailResponse;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.portfolio.dto.PortfolioPerformanceResponse;
import com.porcana.domain.portfolio.entity.PortfolioStatus;
import com.porcana.domain.user.entity.UserRole;
import com.porcana.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Admin", description = "관리자 API")
@SecurityRequirement(name = "JWT")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@Validated
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @Operation(summary = "관리자 생성", description = "새로운 관리자 계정을 생성합니다.")
    @PostMapping("/admins")
    public ResponseEntity<AdminUserDetailResponse> createAdmin(
            @Valid @RequestBody CreateAdminRequest request) {
        AdminUserDetailResponse response = adminService.createAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "회원 목록 조회", description = "회원 목록을 조회합니다. 검색어와 역할 필터를 지원합니다.")
    @GetMapping("/users")
    public ResponseEntity<AdminUserListResponse> getUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "이메일 또는 닉네임 검색어") @RequestParam(required = false) String keyword,
            @Parameter(description = "역할 필터 (USER, ADMIN)") @RequestParam(required = false) UserRole role) {
        AdminUserListResponse response = adminService.getUsers(pageable, keyword, role);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "회원 상세 조회", description = "회원의 상세 정보를 조회합니다.")
    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminUserDetailResponse> getUserDetail(@PathVariable UUID userId) {
        AdminUserDetailResponse response = adminService.getUserDetail(userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "회원 삭제", description = "회원을 soft delete 처리합니다.")
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID userId,
            @CurrentUser UUID adminUserId) {
        adminService.deleteUser(userId, adminUserId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "종목 목록 조회", description = "종목 목록을 조회합니다. 검색어와 시장 필터를 지원합니다.")
    @GetMapping("/assets")
    public ResponseEntity<AdminAssetListResponse> getAssets(
            @PageableDefault(size = 20, sort = "symbol", direction = Sort.Direction.ASC) Pageable pageable,
            @Parameter(description = "심볼 또는 이름 검색어") @RequestParam(required = false) String keyword,
            @Parameter(description = "시장 필터 (KR, US)") @RequestParam(required = false) Asset.Market market,
            @Parameter(description = "종목 타입 필터 (STOCK, ETF)") @RequestParam(required = false) Asset.AssetType type) {
        AdminAssetListResponse response = adminService.getAssets(pageable, keyword, market, type);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "종목 상세 조회", description = "관리자용 종목 상세 정보를 조회합니다.")
    @GetMapping("/assets/{assetId}")
    public ResponseEntity<AssetDetailResponse> getAssetDetail(@PathVariable UUID assetId) {
        return ResponseEntity.ok(adminService.getAssetDetail(assetId));
    }

    @Operation(summary = "종목 가격 차트 조회", description = "관리자용 종목 가격 차트를 조회합니다.")
    @GetMapping("/assets/{assetId}/chart")
    public ResponseEntity<AssetChartResponse> getAssetChart(
            @PathVariable UUID assetId,
            @RequestParam
            @Pattern(regexp = "1M|3M|1Y", message = "range must be one of 1M, 3M, 1Y")
            String range) {
        return ResponseEntity.ok(adminService.getAssetChart(assetId, range));
    }

    @Operation(summary = "종목 이미지 수정", description = "종목 이미지 URL을 수정합니다.")
    @PatchMapping("/assets/{assetId}/image")
    public ResponseEntity<Void> updateAssetImage(
            @PathVariable UUID assetId,
            @Valid @RequestBody UpdateAssetImageRequest request) {
        adminService.updateAssetImage(assetId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "종목 배당 정보 수정", description = "종목 배당 관련 정보를 수정합니다.")
    @PatchMapping("/assets/{assetId}/dividend")
    public ResponseEntity<Void> updateAssetDividend(
            @PathVariable UUID assetId,
            @Valid @RequestBody UpdateAssetDividendRequest request) {
        adminService.updateAssetDividend(assetId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "포트폴리오 목록 조회", description = "전체 포트폴리오 목록을 조회합니다.")
    @GetMapping("/portfolios")
    public ResponseEntity<AdminPortfolioListResponse> getPortfolios(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "포트폴리오 이름 검색어") @RequestParam(required = false) String keyword,
            @Parameter(description = "상태 필터 (ACTIVE, FINISHED)") @RequestParam(required = false) PortfolioStatus status) {
        AdminPortfolioListResponse response = adminService.getPortfolios(pageable, keyword, status);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "포트폴리오 상세 조회", description = "포트폴리오의 상세 정보를 조회합니다.")
    @GetMapping("/portfolios/{portfolioId}")
    public ResponseEntity<AdminPortfolioDetailResponse> getPortfolioDetail(@PathVariable UUID portfolioId) {
        AdminPortfolioDetailResponse response = adminService.getPortfolioDetail(portfolioId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "포트폴리오 수익률 차트 조회", description = "관리자용 포트폴리오 수익률 차트를 조회합니다.")
    @GetMapping("/portfolios/{portfolioId}/performance")
    public ResponseEntity<PortfolioPerformanceResponse> getPortfolioPerformance(
            @PathVariable UUID portfolioId,
            @RequestParam
            @Pattern(regexp = "1M|3M|1Y", message = "range must be one of 1M, 3M, 1Y")
            String range) {
        return ResponseEntity.ok(adminService.getPortfolioPerformance(portfolioId, range));
    }

    @Operation(summary = "배치 실행 로그 목록", description = "배치 실행 로그를 조회합니다.")
    @GetMapping("/batch-runs")
    public ResponseEntity<AdminBatchRunListResponse> getBatchRuns(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adminService.getBatchRuns(pageable));
    }

    @Operation(summary = "배치 실행 로그 상세", description = "배치 실행 로그와 이슈 목록을 조회합니다.")
    @GetMapping("/batch-runs/{runId}")
    public ResponseEntity<AdminBatchRunDetailResponse> getBatchRunDetail(@PathVariable UUID runId) {
        return ResponseEntity.ok(adminService.getBatchRunDetail(runId));
    }

    @Operation(summary = "오늘 배치 이슈 목록", description = "오늘 생성된 배치 이슈 목록을 조회합니다.")
    @GetMapping("/batch-runs/today-issues")
    public ResponseEntity<AdminTodayBatchIssueListResponse> getTodayBatchIssues() {
        return ResponseEntity.ok(adminService.getTodayBatchIssues());
    }
}
