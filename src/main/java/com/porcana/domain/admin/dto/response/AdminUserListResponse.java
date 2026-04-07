package com.porcana.domain.admin.dto.response;

import com.porcana.domain.user.entity.User;
import com.porcana.domain.user.entity.UserRole;
import lombok.Builder;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Paginated user list response for admin
 */
@Builder
public record AdminUserListResponse(
        List<UserItem> users,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    @Builder
    public record UserItem(
            UUID userId,
            String email,
            String nickname,
            UserRole role,
            User.AuthProvider provider,
            LocalDateTime createdAt,
            LocalDateTime deletedAt
    ) {
        public static UserItem from(User user) {
            return UserItem.builder()
                    .userId(user.getId())
                    .email(user.getEmail())
                    .nickname(user.getNickname())
                    .role(user.getRole())
                    .provider(user.getProvider())
                    .createdAt(user.getCreatedAt())
                    .deletedAt(user.getDeletedAt())
                    .build();
        }
    }

    public static AdminUserListResponse from(Page<User> page) {
        return AdminUserListResponse.builder()
                .users(page.getContent().stream().map(UserItem::from).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
