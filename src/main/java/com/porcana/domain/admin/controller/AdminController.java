package com.porcana.domain.admin.controller;

import com.porcana.domain.admin.dto.request.CreateAdminRequest;
import com.porcana.domain.admin.dto.request.UpdateAssetDividendRequest;
import com.porcana.domain.admin.dto.request.UpdateAssetImageRequest;
import com.porcana.domain.admin.dto.response.*;
import com.porcana.domain.admin.service.AdminService;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.user.entity.UserRole;
import com.porcana.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin", description = "관리자 API")
@SecurityRequirement(name = "JWT")
@PreAuthorize("hasRole('ADMIN')")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    // ========================================
    // Admin Management
    // ========================================

    @Operation(summary = "새 관리자 생성", description = "새로운 관리자 계정을 생성합니다.")
    @PostMapping("/admins")
    public ResponseEntity<AdminUserDetailResponse> createAdmin(
            @Valid @RequestBody CreateAdminRequest request) {
        AdminUserDetailResponse response = adminService.createAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ========================================
    // User Management
    // ========================================

    @Operation(summary = "회원 목록 조회", description = "회원 목록을 조회합니다. 검색 및 역할 필터링을 지원합니다.")
    @GetMapping("/users")
    public ResponseEntity<AdminUserListResponse> getUsers(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "이메일 또는 닉네임 검색") @RequestParam(required = false) String keyword,
            @Parameter(description = "역할 필터 (USER, ADMIN)") @RequestParam(required = false) UserRole role) {
        AdminUserListResponse response = adminService.getUsers(pageable, keyword, role);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "회원 상세 조회", description = "회원의 상세 정보를 조회합니다.")
    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminUserDetailResponse> getUserDetail(
            @PathVariable UUID userId) {
        AdminUserDetailResponse response = adminService.getUserDetail(userId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "회원 탈퇴 처리", description = "회원을 탈퇴 처리합니다. (Soft Delete)")
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable UUID userId,
            @CurrentUser UUID adminUserId) {
        adminService.deleteUser(userId, adminUserId);
        return ResponseEntity.noContent().build();
    }

    // ========================================
    // Asset Management
    // ========================================

    @Operation(summary = "종목 목록 조회", description = "종목 목록을 조회합니다. 검색 및 시장 필터링을 지원합니다.")
    @GetMapping("/assets")
    public ResponseEntity<AdminAssetListResponse> getAssets(
            @PageableDefault(size = 20, sort = "symbol", direction = Sort.Direction.ASC) Pageable pageable,
            @Parameter(description = "심볼 또는 이름 검색") @RequestParam(required = false) String keyword,
            @Parameter(description = "시장 필터 (KR, US)") @RequestParam(required = false) Asset.Market market) {
        AdminAssetListResponse response = adminService.getAssets(pageable, keyword, market);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "종목 이미지 수정", description = "종목의 이미지 URL을 수정합니다.")
    @PatchMapping("/assets/{assetId}/image")
    public ResponseEntity<Void> updateAssetImage(
            @PathVariable UUID assetId,
            @Valid @RequestBody UpdateAssetImageRequest request) {
        adminService.updateAssetImage(assetId, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "종목 배당 정보 수정", description = "종목의 배당 관련 정보를 수정합니다.")
    @PatchMapping("/assets/{assetId}/dividend")
    public ResponseEntity<Void> updateAssetDividend(
            @PathVariable UUID assetId,
            @Valid @RequestBody UpdateAssetDividendRequest request) {
        adminService.updateAssetDividend(assetId, request);
        return ResponseEntity.ok().build();
    }

    // ========================================
    // Portfolio Management
    // ========================================

    @Operation(summary = "포트폴리오 목록 조회", description = "전체 포트폴리오 목록을 조회합니다.")
    @GetMapping("/portfolios")
    public ResponseEntity<AdminPortfolioListResponse> getPortfolios(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "포트폴리오 이름 검색") @RequestParam(required = false) String keyword) {
        AdminPortfolioListResponse response = adminService.getPortfolios(pageable, keyword);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "포트폴리오 상세 조회", description = "포트폴리오의 상세 정보를 조회합니다. (소유권 체크 없음)")
    @GetMapping("/portfolios/{portfolioId}")
    public ResponseEntity<AdminPortfolioDetailResponse> getPortfolioDetail(
            @PathVariable UUID portfolioId) {
        AdminPortfolioDetailResponse response = adminService.getPortfolioDetail(portfolioId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "배치 실행 로그 목록", description = "어드민에서 배치 실행 로그를 조회합니다.")
    @GetMapping("/batch-runs")
    public ResponseEntity<AdminBatchRunListResponse> getBatchRuns(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(adminService.getBatchRuns(pageable));
    }

    @Operation(summary = "배치 실행 로그 상세", description = "배치 실행 로그와 이상 종목 목록을 조회합니다.")
    @GetMapping("/batch-runs/{runId}")
    public ResponseEntity<AdminBatchRunDetailResponse> getBatchRunDetail(
            @PathVariable UUID runId) {
        return ResponseEntity.ok(adminService.getBatchRunDetail(runId));
    }

    @Operation(summary = "오늘 배치 이상 종목 목록", description = "오늘 생성된 배치 이상 종목 목록을 조회합니다.")
    @GetMapping("/batch-runs/today-issues")
    public ResponseEntity<AdminTodayBatchIssueListResponse> getTodayBatchIssues() {
        return ResponseEntity.ok(adminService.getTodayBatchIssues());
    }
}
