package com.porcana.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyEmailRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9]{8}$", message = "인증 코드 형식이 올바르지 않습니다")
        String token
) {}