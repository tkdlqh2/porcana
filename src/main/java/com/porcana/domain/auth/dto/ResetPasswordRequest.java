package com.porcana.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9]{16}$", message = "재설정 코드 형식이 올바르지 않습니다")
        String token,

        @NotBlank
        @Size(min = 8, message = "비밀번호는 최소 8자 이상이어야 합니다")
        String newPassword
) {}