package com.porcana.domain.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Apple OAuth2 Provider implementation
 * Verifies Apple authorization code and extracts user email
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppleOAuth2Provider implements OAuth2Provider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${oauth.apple.client-id:}")
    private String clientId;

    @Value("${oauth.apple.team-id:}")
    private String teamId;

    @Value("${oauth.apple.key-id:}")
    private String keyId;

    @Value("${oauth.apple.private-key:}")
    private String privateKey;

    @Value("${oauth.apple.redirect-uri:}")
    private String redirectUri;

    private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";

    @Override
    public String verifyAndGetEmail(String code) {
        try {
            // Step 1: Generate client secret (JWT)
            String clientSecret = generateClientSecret();

            // Step 2: Exchange authorization code for ID token
            String idToken = exchangeCodeForIdToken(code, clientSecret);

            // Step 3: Parse ID token and extract email
            return extractEmailFromIdToken(idToken);

        } catch (Exception e) {
            log.error("Failed to verify Apple OAuth code", e);
            throw new IllegalArgumentException("Invalid Apple authorization code");
        }
    }

    @Override
    public String getProviderName() {
        return "APPLE";
    }

    /**
     * Generate client secret JWT for Apple OAuth
     * Apple requires a client secret signed with your private key
     */
    private String generateClientSecret() {
        try {
            Instant now = Instant.now();
            Instant expiration = now.plusSeconds(15777000); // 6 months

            // Parse private key
            PrivateKey key = parsePrivateKey(privateKey);

            // Generate JWT
            return Jwts.builder()
                    .setHeaderParam("kid", keyId)
                    .setHeaderParam("alg", "ES256")
                    .setIssuer(teamId)
                    .setIssuedAt(Date.from(now))
                    .setExpiration(Date.from(expiration))
                    .setAudience("https://appleid.apple.com")
                    .setSubject(clientId)
                    .signWith(key, SignatureAlgorithm.ES256)
                    .compact();

        } catch (Exception e) {
            log.error("Failed to generate Apple client secret", e);
            throw new IllegalArgumentException("Failed to generate Apple client secret");
        }
    }

    /**
     * Parse PKCS8 private key from PEM string
     */
    private PrivateKey parsePrivateKey(String privateKeyPem) throws Exception {
        String privateKeyContent = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");

        byte[] keyBytes = Base64.getDecoder().decode(privateKeyContent);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");

        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * Exchange authorization code for ID token
     */
    private String exchangeCodeForIdToken(String code, String clientSecret) {
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
            return jsonNode.get("id_token").asText();
        } catch (Exception e) {
            log.error("Failed to parse Apple token response", e);
            throw new IllegalArgumentException("Invalid token response from Apple");
        }
    }

    /**
     * Extract email from Apple ID token (JWT)
     */
    private String extractEmailFromIdToken(String idToken) {
        try {
            // Decode JWT payload (without verification since we trust Apple's response)
            String[] parts = idToken.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT format");
            }

            String payload = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8
            );

            JsonNode jsonNode = objectMapper.readTree(payload);
            String email = jsonNode.get("email").asText();

            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Email not found in Apple ID token");
            }

            return email;

        } catch (Exception e) {
            log.error("Failed to extract email from Apple ID token", e);
            throw new IllegalArgumentException("Invalid Apple ID token");
        }
    }
}