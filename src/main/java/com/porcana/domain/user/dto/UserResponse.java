package com.porcana.domain.user.dto;

import com.porcana.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UserResponse {
    private UUID userId;
    private String nickname;
    private UUID mainPortfolioId;

    public static UserResponse from(User user) {
        return UserResponse.builder()
                .userId(user.getId())
                .nickname(user.getNickname())
                .mainPortfolioId(user.getMainPortfolioId())
                .build();
    }
}