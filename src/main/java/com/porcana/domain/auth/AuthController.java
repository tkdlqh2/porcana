package com.porcana.domain.auth;

import com.porcana.domain.auth.dto.LoginRequest;
import com.porcana.domain.auth.dto.RefreshRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Auth Controller
 * Base Path: /app/v1/auth
 */
@RestController
@RequestMapping("/app/v1/auth")
public class AuthController {

    /**
     * POST /app/v1/auth/login
     * Request: { provider, code?, email?, password? }
     * Response: { accessToken, refreshToken }
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // TODO: Implement login logic
        return ResponseEntity.status(501).body("Not implemented");
    }

    /**
     * POST /app/v1/auth/refresh
     * Request: { refreshToken }
     * Response: { accessToken, refreshToken }
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        // TODO: Implement token refresh logic
        return ResponseEntity.status(501).body("Not implemented");
    }
}