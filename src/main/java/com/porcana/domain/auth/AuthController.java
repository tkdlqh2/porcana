package com.porcana.domain.auth;

import com.porcana.domain.auth.command.LoginCommand;
import com.porcana.domain.auth.command.SignupCommand;
import com.porcana.domain.auth.dto.AuthResponse;
import com.porcana.domain.auth.dto.LoginRequest;
import com.porcana.domain.auth.dto.RefreshRequest;
import com.porcana.domain.auth.dto.SignupRequest;
import com.porcana.domain.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Validated
@Tag(name = "Auth", description = "인증 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    private static final String GUEST_COOKIE_NAME = "porcana_guest";

    @Operation(
            summary = "회원가입",
            description = "이메일로 회원가입하고 JWT 토큰을 발급받습니다. 게스트 세션이 있으면 자동으로 포트폴리오를 사용자 계정으로 이전합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "회원가입 성공"),
                    @ApiResponse(responseCode = "400", description = "이메일 중복 또는 잘못된 요청", content = @Content)
            }
    )
    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(
            @Valid @RequestBody SignupRequest request,
            HttpServletRequest httpRequest
    ) {
        SignupCommand command = SignupCommand.from(request);
        AuthResponse response = authService.signup(command);

        // Claim guest data if guest session cookie exists
        UUID guestSessionId = extractGuestSessionId(httpRequest);
        if (guestSessionId != null) {
            authService.claimGuestData(guestSessionId, response.getUser().getUserId());
        }

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "로그인",
            description = "이메일과 비밀번호로 로그인하고 JWT 토큰을 발급받습니다. 게스트 세션이 있으면 자동으로 포트폴리오를 사용자 계정으로 이전합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "로그인 성공"),
                    @ApiResponse(responseCode = "400", description = "이메일 또는 비밀번호 오류", content = @Content)
            }
    )
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        LoginCommand command = LoginCommand.from(request);
        AuthResponse response = authService.login(command);

        // Claim guest data if guest session cookie exists
        UUID guestSessionId = extractGuestSessionId(httpRequest);
        if (guestSessionId != null) {
            authService.claimGuestData(guestSessionId, response.getUser().getUserId());
        }

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "이메일 중복 확인",
            description = "회원가입 시 이메일 사용 가능 여부를 확인합니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "조회 성공")
            }
    )
    @GetMapping("/check-email")
    public ResponseEntity<Map<String, Boolean>> checkEmail(
            @Parameter(description = "확인할 이메일 주소", required = true)
            @RequestParam @Email String email
    ) {
        boolean available = authService.isEmailAvailable(email);
        return ResponseEntity.ok(Map.of("available", available));
    }

    @Operation(
            summary = "토큰 갱신",
            description = "Refresh Token으로 새로운 Access Token과 Refresh Token을 발급받습니다.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
                    @ApiResponse(responseCode = "400", description = "유효하지 않은 Refresh Token", content = @Content)
            }
    )
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthResponse response = authService.refresh(request.refreshToken());
        return ResponseEntity.ok(response);
    }

    /**
     * Extract guest session ID from cookie
     */
    private UUID extractGuestSessionId(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        return Arrays.stream(cookies)
                .filter(cookie -> GUEST_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .map(this::parseUUID)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /**
     * Parse UUID from string, return null if invalid
     */
    private UUID parseUUID(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID in guest cookie: {}", value);
            return null;
        }
    }
}