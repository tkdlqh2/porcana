package com.porcana.domain.auth.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = LoginRequestValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidLoginRequest {
    String message() default "Invalid login request";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}