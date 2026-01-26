package com.porcana.domain.guest;

import com.porcana.domain.guest.dto.GuestSessionResponse;
import com.porcana.domain.guest.service.GuestSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Guest Session", description = "게스트 세션 API")
@RestController
@RequestMapping("/api/v1/guest-sessions")
@RequiredArgsConstructor
public class GuestSessionController {

    private final GuestSessionService guestSessionService;

    public static final String GUEST_COOKIE_NAME = "porcana_guest";
    public static final int GUEST_COOKIE_MAX_AGE = 30 * 24 * 60 * 60; // 30 days in seconds

    @Operation(
            summary = "게스트 세션 생성",
            description = "비회원을 위한 게스트 세션을 생성하고 쿠키를 설정합니다.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "게스트 세션 생성 성공"),
                    @ApiResponse(responseCode = "500", description = "서버 오류", content = @Content)
            }
    )
    @PostMapping
    public ResponseEntity<GuestSessionResponse> createGuestSession(HttpServletResponse response) {
        // Create new guest session
        UUID guestSessionId = guestSessionService.createGuestSession();

        // Set cookie
        Cookie cookie = new Cookie(GUEST_COOKIE_NAME, guestSessionId.toString());
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(GUEST_COOKIE_MAX_AGE);
        cookie.setSecure(true); // HTTPS 환경에서만 쿠키 전송
        cookie.setAttribute("SameSite", "Lax"); // CSRF 방지
        response.addCookie(cookie);

        // Return response (guestSessionId for debugging purposes)
        GuestSessionResponse guestSessionResponse = new GuestSessionResponse(guestSessionId);
        return ResponseEntity.status(HttpStatus.CREATED).body(guestSessionResponse);
    }
}
