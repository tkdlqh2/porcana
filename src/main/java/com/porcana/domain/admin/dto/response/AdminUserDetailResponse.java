package com.porcana.domain.admin.dto.response;

import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.entity.UserRole;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * User detail response for admin with additional stats
 */
@Builder
public record AdminUserDetailResponse(
        UUID userId,
        String email,
        String nickname,
        UserRole role,
        User.AuthProvider provider,
        UUID mainPortfolioId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime deletedAt,
        // Stats
        int portfolioCount,
        int arenaSessionCount
) {
    public static AdminUserDetailResponse from(User user, int portfolioCount, int arenaSessionCount) {
        return AdminUserDetailResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole())
                .provider(user.getProvider())
                .mainPortfolioId(user.getMainPortfolioId())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .deletedAt(user.getDeletedAt())
                .portfolioCount(portfolioCount)
                .arenaSessionCount(arenaSessionCount)
                .build();
    }
}
