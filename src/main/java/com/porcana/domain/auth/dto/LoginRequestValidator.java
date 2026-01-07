package com.porcana.domain.auth.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class LoginRequestValidator implements ConstraintValidator<ValidLoginRequest, LoginRequest> {

    @Override
    public boolean isValid(LoginRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        // provider가 EMAIL인 경우 email과 password 필수
        if ("EMAIL".equals(request.provider())) {
            if (request.email() == null || request.email().isBlank()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("이메일은 필수입니다")
                        .addPropertyNode("email")
                        .addConstraintViolation();
                return false;
            }
            if (request.password() == null || request.password().isBlank()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("비밀번호는 필수입니다")
                        .addPropertyNode("password")
                        .addConstraintViolation();
                return false;
            }
        }

        // provider가 GOOGLE 또는 APPLE인 경우 code 필수
        if ("GOOGLE".equals(request.provider()) || "APPLE".equals(request.provider())) {
            if (request.code() == null || request.code().isBlank()) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("인증 코드는 필수입니다")
                        .addPropertyNode("code")
                        .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}