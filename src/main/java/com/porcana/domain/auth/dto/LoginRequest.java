package com.porcana.domain.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;

@ValidLoginRequest
public record LoginRequest(
        @Pattern(regexp = "^(EMAIL|GOOGLE|APPLE)$", message = "provider는 EMAIL, GOOGLE, APPLE 중 하나여야 합니다")
        String provider,

        String code,

        @Email(message = "올바른 이메일 형식이 아닙니다")
        String email,

        String password
) {
}