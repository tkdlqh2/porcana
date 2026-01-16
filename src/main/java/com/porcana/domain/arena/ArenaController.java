package com.porcana.domain.arena;

import com.porcana.domain.arena.command.CreateSessionCommand;
import com.porcana.domain.arena.command.PickAssetCommand;
import com.porcana.domain.arena.command.PickRiskProfileCommand;
import com.porcana.domain.arena.command.PickSectorsCommand;
import com.porcana.domain.arena.dto.*;
import com.porcana.domain.arena.service.ArenaService;
import com.porcana.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Arena Controller (Hearthstone-style drafting)
 * Base Path: /api/v1/arena
 */
@RestController
@RequestMapping("/api/v1/arena")
@RequiredArgsConstructor
@SecurityRequirement(name = "JWT")
public class ArenaController {

    private final ArenaService arenaService;

    /**
     * POST /api/v1/arena/sessions
     * Request: { portfolioId }
     * Response: { sessionId, portfolioId, status, currentRound }
     */
    @PostMapping("/sessions")
    public ResponseEntity<CreateSessionResponse> createSession(
            @RequestBody @Valid CreateSessionRequest request,
            @CurrentUser UUID userId) {

        CreateSessionCommand command = CreateSessionCommand.from(request, userId);
        CreateSessionResponse response = arenaService.createSession(command);

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/arena/sessions/{sessionId}
     * Response: { sessionId, portfolioId, status, currentRound, totalRounds, riskProfile, selectedSectors, selectedAssetIds }
     */
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(
            @PathVariable UUID sessionId,
            @CurrentUser UUID userId) {

        SessionResponse response = arenaService.getSession(sessionId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/arena/sessions/{sessionId}/rounds/current
     * Response varies by round type:
     * - Round 1: { sessionId, round, roundType, options: [RiskProfileOption] }
     * - Round 2: { sessionId, round, roundType, sectors: [SectorOption], minSelection, maxSelection }
     * - Round 3-12: { sessionId, round, roundType, assets: [AssetOption] }
     */
    @GetMapping("/sessions/{sessionId}/rounds/current")
    public ResponseEntity<RoundResponse> getCurrentRound(
            @PathVariable UUID sessionId,
            @CurrentUser UUID userId) {

        RoundResponse response = arenaService.getCurrentRound(sessionId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/arena/sessions/{sessionId}/rounds/current/pick-risk-profile
     * Request: { riskProfile: "AGGRESSIVE" | "BALANCED" | "CONSERVATIVE" }
     * Response: { sessionId, status, currentRound, picked }
     */
    @PostMapping("/sessions/{sessionId}/rounds/current/pick-risk-profile")
    public ResponseEntity<PickResponse> pickRiskProfile(
            @PathVariable UUID sessionId,
            @RequestBody @Valid PickRiskProfileRequest request,
            @CurrentUser UUID userId) {

        PickRiskProfileCommand command = PickRiskProfileCommand.from(request);
        PickResponse response = arenaService.pickRiskProfile(sessionId, userId, command);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/arena/sessions/{sessionId}/rounds/current/pick-sectors
     * Request: { sectors: ["INFORMATION_TECHNOLOGY", "HEALTH_CARE"] }  (2-3 sectors)
     * Response: { sessionId, status, currentRound, picked }
     */
    @PostMapping("/sessions/{sessionId}/rounds/current/pick-sectors")
    public ResponseEntity<PickResponse> pickSectors(
            @PathVariable UUID sessionId,
            @RequestBody @Valid PickSectorsRequest request,
            @CurrentUser UUID userId) {

        PickSectorsCommand command = PickSectorsCommand.from(request);
        PickResponse response = arenaService.pickSectors(sessionId, userId, command);

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/arena/sessions/{sessionId}/rounds/current/pick-asset
     * Request: { pickedAssetId }
     * Response: { sessionId, status, currentRound, picked }
     */
    @PostMapping("/sessions/{sessionId}/rounds/current/pick-asset")
    public ResponseEntity<PickResponse> pickAsset(
            @PathVariable UUID sessionId,
            @RequestBody @Valid PickAssetRequest request,
            @CurrentUser UUID userId) {

        PickAssetCommand command = PickAssetCommand.from(request);
        PickResponse response = arenaService.pickAsset(sessionId, userId, command);

        return ResponseEntity.ok(response);
    }
}
