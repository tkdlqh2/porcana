package com.porcana.global.guest;

import com.porcana.domain.guest.service.GuestSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * 게스트 세션 자동 관리 인터셉터
 * 모든 요청에서 게스트 세션 헤더를 확인하고, 필요시 자동 생성
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuestSessionInterceptor implements HandlerInterceptor {

    private final GuestSessionService guestSessionService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Skip for auth endpoints and guest session creation endpoint
        String requestURI = request.getRequestURI();
        if (requestURI.startsWith("/api/v1/auth") || requestURI.startsWith("/api/v1/guest-sessions")) {
            return true;
        }

        // Check for existing guest session header
        String headerValue = request.getHeader(GuestSessionExtractor.GUEST_SESSION_HEADER);
        UUID guestSessionId = parseUUID(headerValue);

        // If valid guest session exists, update last_seen_at
        if (guestSessionId != null && isValidGuestSession(guestSessionId)) {
            guestSessionService.updateLastSeenAt(guestSessionId);
            log.debug("Updated guest session: {}", guestSessionId);
        } else {
            // Create new guest session if not authenticated
            if (request.getHeader("Authorization") == null) {
                UUID newGuestSessionId = guestSessionService.createGuestSession();
                response.setHeader(GuestSessionExtractor.GUEST_SESSION_HEADER, newGuestSessionId.toString());
                log.info("Created new guest session: {}", newGuestSessionId);
            }
        }

        return true;
    }

    /**
     * Parse UUID from string
     */
    private UUID parseUUID(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID in guest session header: {}", value);
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
}
