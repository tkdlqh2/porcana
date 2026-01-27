package com.porcana.domain.auth.oauth;

/**
 * OAuth2 Provider interface
 * Handles OAuth2 authorization code verification and user info extraction
 */
public interface OAuth2Provider {

    /**
     * Verify OAuth2 authorization code and get user email
     *
     * @param code Authorization code from OAuth provider
     * @return User email from OAuth provider
     * @throws IllegalArgumentException if code is invalid or verification fails
     */
    String verifyAndGetEmail(String code);

    /**
     * Get provider name
     *
     * @return Provider name (GOOGLE, APPLE)
     */
    String getProviderName();
}