package com.porcana.domain.arena;

import com.porcana.domain.arena.dto.CreateSessionRequest;
import com.porcana.domain.arena.dto.PickAssetRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Arena Controller (Hearthstone-style drafting)
 * Base Path: /app/v1/arena
 */
@RestController
@RequestMapping("/arena")
public class ArenaController {

    /**
     * POST /app/v1/arena/sessions
     * Request: { portfolioId }
     * Response: { sessionId, portfolioId, status, currentRound }
     */
    @PostMapping("/sessions")
    public ResponseEntity<?> createSession(@RequestBody CreateSessionRequest request) {
        // TODO: Implement create arena session logic
        return ResponseEntity.status(501).body("Not implemented");
    }

    /**
     * GET /app/v1/arena/sessions/{sessionId}
     * Response: { sessionId, portfolioId, status, currentRound, totalRounds }
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<?> getSession(@PathVariable String sessionId) {
        // TODO: Implement get session logic
        return ResponseEntity.status(501).body("Not implemented");
    }

    /**
     * GET /app/v1/arena/sessions/{sessionId}/rounds/current
     * Response: { sessionId, round, assets: [{ assetId, ticker, name, oneLineThesis, tags }] }
     */
    @GetMapping("/sessions/{sessionId}/rounds/current")
    public ResponseEntity<?> getCurrentRound(@PathVariable String sessionId) {
        // TODO: Implement get current round logic
        return ResponseEntity.status(501).body("Not implemented");
    }

    /**
     * POST /app/v1/arena/sessions/{sessionId}/rounds/current/pick
     * Request: { pickedAssetId }
     * Response: { sessionId, status, currentRound, picked: { assetId } }
     */
    @PostMapping("/sessions/{sessionId}/rounds/current/pick")
    public ResponseEntity<?> pickAsset(
            @PathVariable String sessionId,
            @RequestBody PickAssetRequest request
    ) {
        // TODO: Implement pick asset logic
        return ResponseEntity.status(501).body("Not implemented");
    }
}