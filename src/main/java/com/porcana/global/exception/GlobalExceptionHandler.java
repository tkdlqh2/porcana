package com.porcana.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationCredentialsNotFoundException(AuthenticationCredentialsNotFoundException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "인증이 필요합니다",
                null
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "접근 권한이 없습니다",
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException occurred: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "BAD_REQUEST",
                ex.getMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException ex) {
        log.warn("IllegalStateException occurred: {}", ex.getMessage(), ex);
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "ILLEGAL_STATE",
                ex.getMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedOperationException(UnsupportedOperationException ex) {
        log.warn("UnsupportedOperationException occurred: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "UNSUPPORTED_OPERATION",
                ex.getMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException ex) {
        log.warn("NotFoundException occurred: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.NOT_FOUND,
                "NOT_FOUND",
                ex.getMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(ForbiddenException ex) {
        log.warn("ForbiddenException occurred: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                ex.getMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler(InvalidOperationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidOperationException(InvalidOperationException ex) {
        log.warn("InvalidOperationException occurred: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "INVALID_OPERATION",
                ex.getMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(InsufficientAssetsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientAssetsException(InsufficientAssetsException ex) {
        log.warn("InsufficientAssetsException occurred: {}", ex.getMessage());
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "INSUFFICIENT_ASSETS",
                ex.getMessage(),
                null
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        log.warn("Validation failed: {}", fieldErrors);

        ErrorResponse error = ErrorResponse.of(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "입력값 검증에 실패했습니다",
                fieldErrors
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("Unexpected exception occurred", ex);
        ErrorResponse error = ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "서버 내부 오류가 발생했습니다",
                null
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    public record ErrorResponse(
            LocalDateTime timestamp,
            int status,
            String code,
            String message,
            Map<String, String> errors
    ) {
        public static ErrorResponse of(HttpStatus status, String code, String message, Map<String, String> errors) {
            return new ErrorResponse(
                    LocalDateTime.now(),
                    status.value(),
                    code,
                    message,
                    errors
            );
        }
    }
}