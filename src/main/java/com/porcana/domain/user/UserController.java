package com.porcana.domain.user;

import com.porcana.domain.user.dto.UpdateUserRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * User Controller
 * Base Path: /app/v1/me
 */
@RestController
@RequestMapping("/app/v1")
public class UserController {

    /**
     * GET /app/v1/me
     * Response: { userId, nickname, mainPortfolioId }
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMe() {
        // TODO: Implement get current user logic
        return ResponseEntity.status(501).body("Not implemented");
    }

    /**
     * PATCH /app/v1/me
     * Request: { nickname }
     * Response: { userId, nickname }
     */
    @PatchMapping("/me")
    public ResponseEntity<?> updateMe(@RequestBody UpdateUserRequest request) {
        // TODO: Implement update user logic
        return ResponseEntity.status(501).body("Not implemented");
    }
}