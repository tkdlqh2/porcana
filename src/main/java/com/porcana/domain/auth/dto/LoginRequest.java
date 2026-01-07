package com.porcana.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @Pattern(regexp = "^(EMAIL|GOOGLE|APPLE)$", message = "provider는 EMAIL, GOOGLE, APPLE 중 하나여야 합니다")
        String provider,

        String code,

        @NotBlank(message = "이메일은 필수입니다")
        @Email(message = "올바른 이메일 형식이 아닙니다")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다")
        String password
) {
}