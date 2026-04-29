package com.porcana.domain.user;

import com.porcana.domain.user.command.UpdateUserCommand;
import com.porcana.domain.user.dto.ChangePasswordRequest;
import com.porcana.domain.user.dto.UpdateUserRequest;
import com.porcana.domain.user.dto.UserResponse;
import com.porcana.domain.user.service.UserService;
import com.porcana.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "User", description = "사용자 정보 API")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@SecurityRequirement(name = "JWT")
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "내 정보 조회",
            description = "현재 인증된 사용자의 최신 정보를 조회합니다. 로그인/회원가입 시 user 정보가 응답에 포함되지만, 이 API는 토큰으로 최신 유저 정보를 조회할 때 사용합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공"),
                    @ApiResponse(responseCode = "401", description = "인증 실패")
            }
    )
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@CurrentUser UUID userId) {
        UserResponse response = userService.getMe(userId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "내 정보 수정",
            description = "현재 로그인한 사용자의 정보를 수정합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "수정 성공"),
                    @ApiResponse(responseCode = "401", description = "인증 실패")
            }
    )
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @CurrentUser UUID userId,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        UpdateUserCommand command = UpdateUserCommand.from(request);
        UserResponse response = userService.updateMe(userId, command);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "비밀번호 변경",
            description = "현재 비밀번호를 확인 후 새 비밀번호로 변경합니다. 이메일 로그인 사용자만 가능합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "변경 성공"),
                    @ApiResponse(responseCode = "400", description = "현재 비밀번호 불일치 또는 소셜 로그인 사용자", content = @Content),
                    @ApiResponse(responseCode = "401", description = "인증 실패")
            }
    )
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @CurrentUser UUID userId,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        userService.changePassword(userId, request.currentPassword(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "회원 탈퇴",
            description = "현재 로그인한 사용자의 계정과 사용자 소유 데이터를 삭제합니다.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "탈퇴 성공"),
                    @ApiResponse(responseCode = "401", description = "인증 실패")
            }
    )
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@CurrentUser UUID userId) {
        userService.deleteMe(userId);
        return ResponseEntity.noContent().build();
    }
}
