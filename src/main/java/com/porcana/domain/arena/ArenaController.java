package com.porcana.domain.arena;

import com.porcana.domain.arena.command.CreateSessionCommand;
import com.porcana.domain.arena.command.PickAssetCommand;
import com.porcana.domain.arena.command.PickRiskProfileCommand;
import com.porcana.domain.arena.command.PickSectorsCommand;
import com.porcana.domain.arena.dto.*;
import com.porcana.domain.arena.service.ArenaService;
import com.porcana.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Arena", description = "아레나 포트폴리오 드래프트 API (Hearthstone-style)")
@RestController
@RequestMapping("/api/v1/arena")
@RequiredArgsConstructor
@SecurityRequirement(name = "JWT")
public class ArenaController {

    private final ArenaService arenaService;

    @Operation(
            summary = "아레나 세션 생성",
            description = "포트폴리오에 대한 새로운 아레나 드래프트 세션을 시작합니다. 이미 진행 중인 세션이 있으면 해당 세션을 반환합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "세션 생성 성공"),
                    @ApiResponse(responseCode = "400", description = "포트폴리오를 찾을 수 없거나 권한이 없음", content = @Content),
                    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
            }
    )
    @PostMapping("/sessions")
    public ResponseEntity<CreateSessionResponse> createSession(
            @RequestBody @Valid CreateSessionRequest request,
            @CurrentUser UUID userId) {

        CreateSessionCommand command = CreateSessionCommand.from(request, userId);
        CreateSessionResponse response = arenaService.createSession(command);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "아레나 세션 조회",
            description = "진행 중이거나 완료된 아레나 세션의 상세 정보를 조회합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "403", description = "세션을 찾을 수 없거나 권한이 없음", content = @Content),
                    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
            }
    )
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<SessionResponse> getSession(
            @Parameter(description = "세션 ID", required = true) @PathVariable UUID sessionId,
            @CurrentUser UUID userId) {

        SessionResponse response = arenaService.getSession(sessionId, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "현재 라운드 조회",
            description = "현재 진행 중인 라운드의 선택지를 조회합니다. Round 1은 리스크 프로필, Round 2는 섹터, Round 3-12는 자산 선택입니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "400", description = "세션이 이미 완료됨", content = @Content),
                    @ApiResponse(responseCode = "403", description = "세션을 찾을 수 없거나 권한이 없음", content = @Content),
                    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
            }
    )
    @GetMapping("/sessions/{sessionId}/rounds/current")
    public ResponseEntity<RoundResponse> getCurrentRound(
            @Parameter(description = "세션 ID", required = true) @PathVariable UUID sessionId,
            @CurrentUser UUID userId) {

        RoundResponse response = arenaService.getCurrentRound(sessionId, userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "리스크 프로필 선택 (Round 1)",
            description = "아레나 Round 1에서 리스크 프로필을 선택합니다. SAFE, BALANCED, AGGRESSIVE 중 선택 가능합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "선택 성공, Round 2로 진행"),
                    @ApiResponse(responseCode = "400", description = "Round 1이 아니거나 유효하지 않은 리스크 프로필", content = @Content),
                    @ApiResponse(responseCode = "403", description = "세션을 찾을 수 없거나 권한이 없음", content = @Content),
                    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
            }
    )
    @PostMapping("/sessions/{sessionId}/rounds/current/pick-risk-profile")
    public ResponseEntity<PickResponse> pickRiskProfile(
            @Parameter(description = "세션 ID", required = true) @PathVariable UUID sessionId,
            @RequestBody @Valid PickRiskProfileRequest request,
            @CurrentUser UUID userId) {

        PickRiskProfileCommand command = PickRiskProfileCommand.from(request);
        PickResponse response = arenaService.pickRiskProfile(sessionId, userId, command);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "섹터 선택 (Round 2)",
            description = "아레나 Round 2에서 관심 섹터를 선택합니다. 0-3개의 섹터를 선택할 수 있으며, 중복은 허용되지 않습니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "선택 성공, Round 3로 진행"),
                    @ApiResponse(responseCode = "400", description = "Round 2가 아니거나 섹터 개수가 3개 초과 또는 중복된 섹터 포함", content = @Content),
                    @ApiResponse(responseCode = "403", description = "세션을 찾을 수 없거나 권한이 없음", content = @Content),
                    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
            }
    )
    @PostMapping("/sessions/{sessionId}/rounds/current/pick-sectors")
    public ResponseEntity<PickResponse> pickSectors(
            @Parameter(description = "세션 ID", required = true) @PathVariable UUID sessionId,
            @RequestBody @Valid PickSectorsRequest request,
            @CurrentUser UUID userId) {

        PickSectorsCommand command = PickSectorsCommand.from(request);
        PickResponse response = arenaService.pickSectors(sessionId, userId, command);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "자산 선택 (Round 3-12)",
            description = "아레나 Round 3-12에서 제시된 3개의 자산 중 1개를 선택합니다. Round 12 완료 시 세션이 종료되고 포트폴리오가 완성됩니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "선택 성공, 다음 라운드로 진행 또는 세션 완료"),
                    @ApiResponse(responseCode = "400", description = "Round 3-12가 아니거나 제시된 자산 목록에 없는 자산 선택", content = @Content),
                    @ApiResponse(responseCode = "403", description = "세션을 찾을 수 없거나 권한이 없음", content = @Content),
                    @ApiResponse(responseCode = "401", description = "인증 필요", content = @Content)
            }
    )
    @PostMapping("/sessions/{sessionId}/rounds/current/pick-asset")
    public ResponseEntity<PickResponse> pickAsset(
            @Parameter(description = "세션 ID", required = true) @PathVariable UUID sessionId,
            @RequestBody @Valid PickAssetRequest request,
            @CurrentUser UUID userId) {

        PickAssetCommand command = PickAssetCommand.from(request);
        PickResponse response = arenaService.pickAsset(sessionId, userId, command);

        return ResponseEntity.ok(response);
    }
}
