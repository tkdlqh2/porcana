package com.porcana.global.guest;

import com.porcana.domain.guest.service.GuestSessionService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.UUID;

/**
 * 게스트 세션 자동 관리 인터셉터
 * 모든 요청에서 게스트 세션 쿠키를 확인하고, 필요시 자동 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestSessionInterceptor implements HandlerInterceptor {

    private final GuestSessionService guestSessionService;

    public static final String GUEST_COOKIE_NAME = "porcana_guest";
    public static final int GUEST_COOKIE_MAX_AGE = 30 * 24 * 60 * 60; // 30 days

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Skip for auth endpoints and guest session creation endpoint
        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/api/v1/auth") || requestURI.startsWith("/api/v1/guest-sessions")) {
            return true;
        }

        // Check for existing guest session cookie
        Cookie[] cookies = request.getCookies();
        UUID guestSessionId = null;

        if (cookies != null) {
            guestSessionId = Arrays.stream(cookies)
                    .filter(cookie -> GUEST_COOKIE_NAME.equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .map(this::parseUUID)
                    .filter(this::isValidGuestSession)
                    .findFirst()
                    .orElse(null);
        }

        // If valid guest session exists, update last_seen_at
        if (guestSessionId != null) {
            guestSessionService.updateLastSeenAt(guestSessionId);
            log.debug("Updated guest session: {}", guestSessionId);
        } else {
            // Create new guest session if not authenticated
            if (request.getHeader("Authorization") == null) {
                UUID newGuestSessionId = guestSessionService.createGuestSession();
                setGuestCookie(response, newGuestSessionId);
                log.info("Created new guest session: {}", newGuestSessionId);
            }
        }

        return true;
    }

    /**
     * Parse UUID from string
     */
    private UUID parseUUID(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID in guest cookie: {}", value);
            return null;
        }
    }

    /**
     * Check if guest session is valid
     */
    private boolean isValidGuestSession(UUID guestSessionId) {
        if (guestSessionId == null) {
            return false;
        }
        return guestSessionService.exists(guestSessionId);
    }

    /**
     * Set guest session cookie
     */
    private void setGuestCookie(HttpServletResponse response, UUID guestSessionId) {
        Cookie cookie = new Cookie(GUEST_COOKIE_NAME, guestSessionId.toString());
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/");
        cookie.setMaxAge(GUEST_COOKIE_MAX_AGE);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
