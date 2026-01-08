package com.porcana.domain.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateUserRequest(
        @NotBlank(message = "닉네임은 필수입니다")
        String nickname
) {
}