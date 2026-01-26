package com.porcana.global.guest;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 게스트 세션 ID 추출 유틸리티
 */
@Slf4j
@Component
public class GuestSessionExtractor {

    public static final String GUEST_SESSION_HEADER = "X-Guest-Session-Id";

    /**
     * Extract guest session ID from header
     * @param request HTTP request
     * @return Guest session ID or null if not found
     */
    public UUID extractGuestSessionId(HttpServletRequest request) {
        String headerValue = request.getHeader(GUEST_SESSION_HEADER);
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }

        return parseUUID(headerValue);
    }

    /**
     * Parse UUID from string, return null if invalid
     */
    private UUID parseUUID(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID in guest session header: {}", value);
            return null;
        }
    }
}
