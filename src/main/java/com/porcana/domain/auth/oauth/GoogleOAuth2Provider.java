package com.porcana.domain.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/**
 * Google OAuth2 Provider implementation
 * Verifies Google authorization code and extracts user email
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleOAuth2Provider implements OAuth2Provider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${oauth.google.client-id:}")
    private String clientId;

    @Value("${oauth.google.client-secret:}")
    private String clientSecret;

    @Value("${oauth.google.redirect-uri:}")
    private String redirectUri;

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo";

    @Override
    public String verifyAndGetEmail(String code) {
        try {
            // Step 1: Exchange authorization code for access token
            String accessToken = exchangeCodeForToken(code);

            // Step 2: Get user info using access token
            return getUserEmail(accessToken);

        } catch (Exception e) {
            log.error("Failed to verify Google OAuth code", e);
            throw new IllegalArgumentException("Invalid Google authorization code");
        }
    }

    @Override
    public String getProviderName() {
        return "GOOGLE";
    }

    /**
     * Exchange authorization code for access token
     */
    private String exchangeCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("code", code);
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                TOKEN_URL,
                HttpMethod.POST,
                request,
                String.class
        );

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new IllegalArgumentException("Failed to exchange code for token");
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            return jsonNode.get("access_token").asText();
        } catch (Exception e) {
            log.error("Failed to parse Google token response", e);
            throw new IllegalArgumentException("Invalid token response from Google");
        }
    }

    /**
     * Get user email using access token
     */
    private String getUserEmail(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                USER_INFO_URL,
                HttpMethod.GET,
                request,
                String.class
        );

        if (response.getStatusCode() != HttpStatus.OK) {
            throw new IllegalArgumentException("Failed to get user info from Google");
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            String email = jsonNode.get("email").asText();

            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Email not found in Google user info");
            }

            return email;
        } catch (Exception e) {
            log.error("Failed to parse Google user info response", e);
            throw new IllegalArgumentException("Invalid user info response from Google");
        }
    }
}