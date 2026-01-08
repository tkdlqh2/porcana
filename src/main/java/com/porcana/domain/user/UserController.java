package com.porcana.domain.user;

import com.porcana.domain.user.command.UpdateUserCommand;
import com.porcana.domain.user.dto.UpdateUserRequest;
import com.porcana.domain.user.dto.UserResponse;
import com.porcana.domain.user.service.UserService;
import com.porcana.global.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "User", description = "사용자 정보 API")
@RestController
@RequestMapping("/app/v1")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(
            summary = "내 정보 조회",
            description = "현재 로그인한 사용자의 정보를 조회합니다.",
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
}