package com.porcana.global.guest;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;

/**
 * 게스트 세션 ID 추출 유틸리티
 */
@Slf4j
@Component
public class GuestSessionExtractor {

    private static final String GUEST_COOKIE_NAME = "porcana_guest";

    /**
     * Extract guest session ID from cookie
     * @param request HTTP request
     * @return Guest session ID or null if not found
     */
    public UUID extractGuestSessionId(HttpServletRequest request) {
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
