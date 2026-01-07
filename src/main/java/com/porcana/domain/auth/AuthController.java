package com.porcana.domain.auth;

import com.porcana.domain.auth.command.LoginCommand;
import com.porcana.domain.auth.command.SignupCommand;
import com.porcana.domain.auth.dto.AuthResponse;
import com.porcana.domain.auth.dto.LoginRequest;
import com.porcana.domain.auth.dto.RefreshRequest;
import com.porcana.domain.auth.dto.SignupRequest;
import com.porcana.domain.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Auth Controller
 * Base Path: /app/v1/auth
 */
@RestController
@RequestMapping("/app/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /app/v1/auth/signup
     * Request: { email, password, nickname }
     * Response: { accessToken, refreshToken }
     */
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@RequestBody SignupRequest request) {
        SignupCommand command = SignupCommand.from(request);
        AuthResponse response = authService.signup(command);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /app/v1/auth/login
     * Request: { provider, code?, email?, password? }
     * Response: { accessToken, refreshToken }
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        LoginCommand command = LoginCommand.from(request);
        AuthResponse response = authService.login(command);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /app/v1/auth/check-email
     * Check if email is already taken
     * Query: email=test@example.com
     * Response: { available: true/false }
     */
    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestParam String email) {
        boolean available = authService.isEmailAvailable(email);
        return ResponseEntity.ok(Map.of("available", available));
    }

    /**
     * POST /app/v1/auth/refresh
     * Request: { refreshToken }
     * Response: { accessToken, refreshToken }
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshRequest request) {
        AuthResponse response = authService.refresh(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }
}