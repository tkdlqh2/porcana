package com.porcana.config;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Mock PasswordEncoder for testing
 * - encode: returns plain text as-is
 * - matches: compares plain text equality
 */
public class MockPasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(CharSequence rawPassword) {
        return rawPassword.toString();
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return rawPassword.toString().equals(encodedPassword);
    }
}