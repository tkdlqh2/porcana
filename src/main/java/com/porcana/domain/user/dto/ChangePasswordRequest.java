package com.porcana.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank
        String currentPassword,

        @NotBlank
        @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
        String newPassword
) {}