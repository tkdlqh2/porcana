package com.porcana.domain.arena;

import com.porcana.domain.arena.command.CreateSessionCommand;
import com.porcana.domain.arena.command.PickAssetCommand;
import com.porcana.domain.arena.command.PickPreferencesCommand;
import com.porcana.domain.arena.dto.*;
import com.porcana.domain.arena.service.ArenaService;
import com.porcana.global.guest.GuestSessionExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Arena", description = "아레나 포트폴리오 드래프트 API (Hearthstone-style)")
@RestController
@RequestMapping("/api/v1/arena")
@RequiredArgsConstructor
@SecurityRequirements({
        @SecurityRequirement(name = "JWT"),
        @SecurityRequirement(name = "GuestSession")
})
public class ArenaController {

    private final ArenaService arenaService;
    private final GuestSessionExtractor guestSessionExtractor;

    @Operation(
            summary = "아레나 세션 생성",
            description = "포트폴리오에 대한 새로운 아레나 드래프트 세션을 시작합니다. 이미 진행 중인 세션이 있으면 해당 세션을 반환합니다. 비회원도 사용 가능합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "세션 생성 성공"),
                    @ApiResponse(responseCode = "400", description = "포트폴리오를 찾을 수 없거나 권한이 없음", content = @Content)
            }
    )
    @PostMapping("/sessions")
    public ResponseEntity<CreateSessionResponse> createSession(
            @RequestBody @Valid CreateSessionRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = extractUserId();
        UUID guestSessionId = guestSessionExtractor.extractGuestSessionId(httpRequest);

        CreateSessionCommand command = CreateSessionCommand.from(request, userId);
        CreateSessionResponse response = arenaService.createSession(command, userId, guestSessionId);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "아레나 세션 조회",
            description = "진행 중이거나 완료된 아레나 세션의 상세 정보를 조회합니다. 비회원도 사용 가능합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "403", description = "세션을 찾을 수 없거나 권한이 없음", content = @Content)
            }
    )
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(
            @Parameter(description = "세션 ID", required = true) @PathVariable UUID sessionId,
            HttpServletRequest request) {

        UUID userId = extractUserId();
        UUID guestSessionId = guestSessionExtractor.extractGuestSessionId(request);

        SessionResponse response = arenaService.getSession(sessionId, userId, guestSessionId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "현재 라운드 조회",
            description = "현재 진행 중인 라운드의 선택지를 조회합니다. Round 0은 투자성향+섹터 동시 선택, Round 1-10은 자산 선택입니다. 비회원도 사용 가능합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "400", description = "세션이 이미 완료됨", content = @Content),
                    @ApiResponse(responseCode = "403", description = "세션을 찾을 수 없거나 권한이 없음", content = @Content)
            }
    )
    @GetMapping("/sessions/{sessionId}/rounds/current")
    public ResponseEntity<RoundResponse> getCurrentRound(
            @Parameter(description = "세션 ID", required = true) @PathVariable UUID sessionId,
            HttpServletRequest request) {

        UUID userId = extractUserId();
        UUID guestSessionId = guestSessionExtractor.extractGuestSessionId(request);

        RoundResponse response = arenaService.getCurrentRound(sessionId, userId, guestSessionId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "투자 성향 및 섹터 선택 (Round 0 - Pre Round)",
            description = "아레나 Round 0에서 투자 성향(리스크 프로필)과 관심 섹터를 동시에 선택합니다. 섹터는 0-3개 선택 가능하며, 중복은 허용되지 않습니다. 비회원도 사용 가능합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "선택 성공, Round 1로 진행"),
                    @ApiResponse(responseCode = "400", description = "Round 0이 아니거나 섹터 개수가 3개 초과 또는 중복된 섹터 포함", content = @Content),
                    @ApiResponse(responseCode = "403", description = "세션을 찾을 수 없거나 권한이 없음", content = @Content)
            }
    )
    @PostMapping("/sessions/{sessionId}/rounds/current/pick-preferences")
    public ResponseEntity<PickResponse> pickPreferences(
            @Parameter(description = "세션 ID", required = true) @PathVariable UUID sessionId,
            @RequestBody @Valid PickPreferencesRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = extractUserId();
        UUID guestSessionId = guestSessionExtractor.extractGuestSessionId(httpRequest);

        PickPreferencesCommand command = PickPreferencesCommand.from(request);
        PickResponse response = arenaService.pickPreferences(sessionId, userId, guestSessionId, command);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "자산 선택 (Round 1-10)",
            description = "아레나 Round 1-10에서 제시된 3개의 자산 중 1개를 선택합니다. Round 10 완료 시 세션이 종료되고 포트폴리오가 완성됩니다. 비회원도 사용 가능합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "선택 성공, 다음 라운드로 진행 또는 세션 완료"),
                    @ApiResponse(responseCode = "400", description = "Round 1-10이 아니거나 제시된 자산 목록에 없는 자산 선택", content = @Content),
                    @ApiResponse(responseCode = "403", description = "세션을 찾을 수 없거나 권한이 없음", content = @Content)
            }
    )
    @PostMapping("/sessions/{sessionId}/rounds/current/pick-asset")
    public ResponseEntity<PickResponse> pickAsset(
            @Parameter(description = "세션 ID", required = true) @PathVariable UUID sessionId,
            @RequestBody @Valid PickAssetRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = extractUserId();
        UUID guestSessionId = guestSessionExtractor.extractGuestSessionId(httpRequest);

        PickAssetCommand command = PickAssetCommand.from(request);
        PickResponse response = arenaService.pickAsset(sessionId, userId, guestSessionId, command);

        return ResponseEntity.ok(response);
    }

    /**
     * Extract user ID from Security Context
     * Returns null if not authenticated
     */
    private UUID extractUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID) {
            return (UUID) principal;
        }

        return null;
    }
}
