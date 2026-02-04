package com.porcana.domain.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/**
 * Google OAuth2 Provider implementation
 * Verifies Google ID Token and extracts user email
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleOAuth2Provider implements OAuth2Provider {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${oauth.google.client-id:}")
    private String clientId;

    private static final String JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String GOOGLE_ISSUER_ALT = "accounts.google.com";

    @Override
    public String verifyAndGetEmail(String idToken) {
        try {
            return extractEmailFromIdToken(idToken);
        } catch (Exception e) {
            log.error("Failed to verify Google ID token", e);
            throw new IllegalArgumentException("Invalid Google ID token");
        }
    }

    @Override
    public String getProviderName() {
        return "GOOGLE";
    }

    /**
     * Extract email from Google ID token (JWT) with signature verification
     */
    private String extractEmailFromIdToken(String idToken) {
        try {
            // Step 1: Get Google's public key from JWKS endpoint
            PublicKey publicKey = getGooglePublicKey(idToken);

            // Step 2: Verify signature and parse claims
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();

            // Step 3: Verify issuer
            String issuer = claims.getIssuer();
            if (!GOOGLE_ISSUER.equals(issuer) && !GOOGLE_ISSUER_ALT.equals(issuer)) {
                throw new IllegalArgumentException("Invalid issuer: " + issuer);
            }

            // Step 4: Verify audience (client ID)
            Object audClaim = claims.get("aud");
            boolean audienceValid = false;
            if (audClaim instanceof String) {
                audienceValid = clientId.equals(audClaim);
            } else if (audClaim instanceof java.util.Collection) {
                audienceValid = ((java.util.Collection<?>) audClaim).contains(clientId);
            }
            if (!audienceValid) {
                log.warn("Audience mismatch - expected: {}, actual: {}", clientId, audClaim);
                throw new IllegalArgumentException("Invalid audience");
            }

            // Step 5: Extract email
            String email = claims.get("email", String.class);
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Email not found in Google ID token");
            }

            log.info("Successfully verified Google ID token for email: {}", email);
            return email;

        } catch (SignatureException e) {
            log.error("Google ID token signature verification failed", e);
            throw new IllegalArgumentException("Invalid Google ID token signature");
        } catch (ExpiredJwtException e) {
            log.error("Google ID token has expired", e);
            throw new IllegalArgumentException("Expired Google ID token");
        } catch (MalformedJwtException e) {
            log.error("Malformed Google ID token", e);
            throw new IllegalArgumentException("Malformed Google ID token");
        }
    }

    /**
     * Get Google's public key from JWKS endpoint for JWT verification
     * Uses the 'kid' (key ID) from JWT header to select the correct key
     */
    private PublicKey getGooglePublicKey(String idToken) {
        try {
            // Step 1: Extract 'kid' from JWT header
            String kid = extractKidFromToken(idToken);

            // Step 2: Fetch JWKS from Google
            ResponseEntity<String> response = restTemplate.getForEntity(JWKS_URL, String.class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new IllegalStateException("Failed to fetch Google JWKS");
            }

            // Step 3: Parse JWKS and find matching key
            JsonNode jwksNode = objectMapper.readTree(response.getBody());
            JsonNode keysNode = jwksNode.get("keys");
            if (keysNode == null || !keysNode.isArray()) {
                throw new IllegalStateException("Invalid JWKS format from Google");
            }

            // Step 4: Find key with matching 'kid'
            for (JsonNode keyNode : keysNode) {
                String keyKid = keyNode.get("kid").asText();
                if (kid.equals(keyKid)) {
                    return buildPublicKey(keyNode);
                }
            }

            throw new IllegalArgumentException("No matching public key found for kid: " + kid);

        } catch (Exception e) {
            log.error("Failed to get Google public key", e);
            throw new IllegalArgumentException("Failed to verify Google ID token");
        }
    }

    /**
     * Extract 'kid' (key ID) from JWT header
     */
    private String extractKidFromToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT format");
            }

            String header = new String(
                    Base64.getUrlDecoder().decode(parts[0]),
                    StandardCharsets.UTF_8
            );

            JsonNode headerNode = objectMapper.readTree(header);
            JsonNode kidNode = headerNode.get("kid");
            if (kidNode == null || kidNode.isNull()) {
                throw new IllegalArgumentException("kid not found in JWT header");
            }

            return kidNode.asText();
        } catch (Exception e) {
            log.error("Failed to extract kid from token", e);
            throw new IllegalArgumentException("Invalid JWT format");
        }
    }

    /**
     * Build RSA public key from JWKS key node
     */
    private PublicKey buildPublicKey(JsonNode keyNode) {
        try {
            String n = keyNode.get("n").asText();
            String e = keyNode.get("e").asText();

            byte[] nBytes = Base64.getUrlDecoder().decode(n);
            byte[] eBytes = Base64.getUrlDecoder().decode(e);

            BigInteger modulus = new BigInteger(1, nBytes);
            BigInteger exponent = new BigInteger(1, eBytes);

            RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            log.error("Failed to build public key from JWKS", e);
            throw new IllegalArgumentException("Failed to build Google public key");
        }
    }
}