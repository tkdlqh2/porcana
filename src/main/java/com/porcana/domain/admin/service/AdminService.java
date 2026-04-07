package com.porcana.domain.admin.service;

import com.porcana.domain.admin.dto.request.CreateAdminRequest;
import com.porcana.domain.admin.dto.request.UpdateAssetDividendRequest;
import com.porcana.domain.admin.dto.request.UpdateAssetImageRequest;
import com.porcana.domain.admin.dto.response.*;
import com.porcana.domain.arena.repository.ArenaSessionRepository;
import com.porcana.domain.asset.AssetRepository;
import com.porcana.domain.asset.entity.Asset;
import com.porcana.domain.portfolio.entity.Portfolio;
import com.porcana.domain.portfolio.entity.PortfolioAsset;
import com.porcana.domain.portfolio.repository.PortfolioAssetRepository;
import com.porcana.domain.portfolio.repository.PortfolioRepository;
import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.entity.UserRole;
import com.porcana.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioAssetRepository portfolioAssetRepository;
    private final ArenaSessionRepository arenaSessionRepository;
    private final AssetRepository assetRepository;
    private final PasswordEncoder passwordEncoder;

    // ========================================
    // Admin Management
    // ========================================

    /**
     * Create a new admin user
     */
    @Transactional
    public AdminUserDetailResponse createAdmin(CreateAdminRequest request) {
        // Check if email is already registered
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exists: " + request.email());
        }

        User admin = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .provider(User.AuthProvider.EMAIL)
                .role(UserRole.ADMIN)
                .build();

        User savedAdmin = userRepository.save(admin);
        log.info("New admin user created: {}", savedAdmin.getEmail());

        return AdminUserDetailResponse.from(savedAdmin, 0, 0);
    }

    // ========================================
    // User Management
    // ========================================

    /**
     * Get paginated user list with optional search
     */
    @Transactional(readOnly = true)
    public AdminUserListResponse getUsers(Pageable pageable, String keyword, UserRole role) {
        Page<User> users;

        if (StringUtils.hasText(keyword) && role != null) {
            users = userRepository.searchByKeywordAndRole(keyword, role, pageable);
        } else if (StringUtils.hasText(keyword)) {
            users = userRepository.searchByKeyword(keyword, pageable);
        } else if (role != null) {
            users = userRepository.findByRoleAndDeletedAtIsNull(role, pageable);
        } else {
            users = userRepository.findByDeletedAtIsNull(pageable);
        }

        return AdminUserListResponse.from(users);
    }

    /**
     * Get user detail with stats
     */
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        int portfolioCount = (int) portfolioRepository.countByUserIdAndDeletedAtIsNull(userId);
        int arenaSessionCount = (int) arenaSessionRepository.countByUserId(userId);

        return AdminUserDetailResponse.from(user, portfolioCount, arenaSessionCount);
    }

    /**
     * Delete (soft delete) a user
     */
    @Transactional
    public void deleteUser(UUID userId, UUID adminUserId) {
        // Prevent self-deletion
        if (userId.equals(adminUserId)) {
            throw new IllegalArgumentException("Cannot delete yourself");
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        user.delete();
        log.info("User deleted by admin: userId={}, adminId={}", userId, adminUserId);
    }

    // ========================================
    // Asset Management
    // ========================================

    /**
     * Get paginated asset list with optional filters
     */
    @Transactional(readOnly = true)
    public AdminAssetListResponse getAssets(Pageable pageable, String keyword, Asset.Market market) {
        Page<Asset> assets;

        if (StringUtils.hasText(keyword)) {
            assets = assetRepository.searchByKeyword(keyword, pageable);
        } else if (market != null) {
            assets = assetRepository.findByMarket(market, pageable);
        } else {
            assets = assetRepository.findAll(pageable);
        }

        return AdminAssetListResponse.from(assets);
    }

    /**
     * Update asset image URL
     */
    @Transactional
    public void updateAssetImage(UUID assetId, UpdateAssetImageRequest request) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        asset.setImageUrl(request.imageUrl());
        log.info("Asset image updated: assetId={}, imageUrl={}", assetId, request.imageUrl());
    }

    /**
     * Update asset dividend information
     */
    @Transactional
    public void updateAssetDividend(UUID assetId, UpdateAssetDividendRequest request) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new IllegalArgumentException("Asset not found: " + assetId));

        asset.updateDividendData(
                request.dividendAvailable(),
                request.dividendYield(),
                request.dividendFrequency(),
                request.dividendCategory(),
                request.dividendDataStatus(),
                request.lastDividendDate()
        );

        log.info("Asset dividend updated: assetId={}", assetId);
    }

    // ========================================
    // Portfolio Management
    // ========================================

    /**
     * Get paginated portfolio list with optional search
     */
    @Transactional(readOnly = true)
    public AdminPortfolioListResponse getPortfolios(Pageable pageable, String keyword) {
        Page<Portfolio> portfolios;

        if (StringUtils.hasText(keyword)) {
            portfolios = portfolioRepository.searchByName(keyword, pageable);
        } else {
            portfolios = portfolioRepository.findByDeletedAtIsNull(pageable);
        }

        return AdminPortfolioListResponse.from(portfolios);
    }

    /**
     * Get portfolio detail (no ownership check - admin only)
     */
    @Transactional(readOnly = true)
    public AdminPortfolioDetailResponse getPortfolioDetail(UUID portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio not found: " + portfolioId));

        // Get owner info
        AdminPortfolioDetailResponse.OwnerInfo ownerInfo;
        if (portfolio.getUserId() != null) {
            User owner = userRepository.findById(portfolio.getUserId())
                    .orElse(null);
            if (owner != null) {
                ownerInfo = AdminPortfolioDetailResponse.OwnerInfo.fromUser(owner);
            } else {
                // User was deleted but portfolio remains
                ownerInfo = AdminPortfolioDetailResponse.OwnerInfo.builder()
                        .ownerType("USER")
                        .userId(portfolio.getUserId())
                        .build();
            }
        } else {
            ownerInfo = AdminPortfolioDetailResponse.OwnerInfo.fromGuestSession(portfolio.getGuestSessionId());
        }

        // Get assets - batch load to avoid N+1 query
        List<PortfolioAsset> portfolioAssets = portfolioAssetRepository.findByPortfolioId(portfolioId);

        // Collect all asset IDs and batch load
        List<UUID> assetIds = portfolioAssets.stream()
                .map(PortfolioAsset::getAssetId)
                .toList();
        Map<UUID, Asset> assetMap = assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, asset -> asset));

        // Map using pre-loaded assets (no additional queries)
        List<AdminPortfolioDetailResponse.AssetItem> assetItems = portfolioAssets.stream()
                .map(pa -> {
                    Asset asset = assetMap.get(pa.getAssetId());
                    String symbol = asset != null ? asset.getSymbol() : "UNKNOWN";
                    String name = asset != null ? asset.getName() : "Unknown Asset";
                    return AdminPortfolioDetailResponse.AssetItem.from(pa, symbol, name);
                })
                .toList();

        return AdminPortfolioDetailResponse.from(portfolio, ownerInfo, assetItems);
    }
}
