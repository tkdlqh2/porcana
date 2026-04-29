package com.porcana.domain.user.dto;

import com.porcana.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UserResponse {
    private UUID userId;
    private String email;
    private String nickname;
    private User.AuthProvider provider;
    private boolean emailVerified;
    private UUID mainPortfolioId;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .provider(user.getProvider())
                .emailVerified(user.isEmailVerified())
                .mainPortfolioId(user.getMainPortfolioId())
                .build();
    }
}