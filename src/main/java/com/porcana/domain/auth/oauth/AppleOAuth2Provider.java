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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
    private static final String JWKS_URL = "https://appleid.apple.com/auth/keys";
    private static final String APPLE_ISSUER = "https://appleid.apple.com";

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
                    .signWith(key, Jwts.SIG.ES256)
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
            JsonNode idTokenNode = jsonNode.get("id_token");
            if (idTokenNode == null || idTokenNode.isNull()) {
                throw new IllegalArgumentException("id_token not found in Apple response");
            }
            return idTokenNode.asText();
        } catch (Exception e) {
            log.error("Failed to parse Apple token response", e);
            throw new IllegalArgumentException("Invalid token response from Apple");
        }
    }

    /**
     * Extract email from Apple ID token (JWT) with signature verification
     * Follows Apple's official OIDC guidelines and OpenID Connect standards
     */
    private String extractEmailFromIdToken(String idToken) {
        try {
            // Step 1: Get Apple's public key from JWKS endpoint
            PublicKey publicKey = getApplePublicKey(idToken);

            // Step 2: Verify signature and parse claims
            Claims claims = Jwts.parser()
                    .verifyWith(publicKey)
                    .requireIssuer(APPLE_ISSUER)
                    .requireAudience(clientId)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();

            // Step 3: Verify expiration (automatically checked by JJWT)
            // Step 4: Extract email
            String email = claims.get("email", String.class);
            if (email == null || email.isBlank()) {
                throw new IllegalArgumentException("Email not found in Apple ID token");
            }

            log.info("Successfully verified Apple ID token for email: {}", email);
            return email;

        } catch (SignatureException e) {
            log.error("Apple ID token signature verification failed", e);
            throw new IllegalArgumentException("Invalid Apple ID token signature");
        } catch (ExpiredJwtException e) {
            log.error("Apple ID token has expired", e);
            throw new IllegalArgumentException("Expired Apple ID token");
        } catch (MalformedJwtException e) {
            log.error("Malformed Apple ID token", e);
            throw new IllegalArgumentException("Malformed Apple ID token");
        } catch (Exception e) {
            log.error("Failed to verify Apple ID token", e);
            throw new IllegalArgumentException("Invalid Apple ID token");
        }
    }

    /**
     * Get Apple's public key from JWKS endpoint for JWT verification
     * Uses the 'kid' (key ID) from JWT header to select the correct key
     */
    private PublicKey getApplePublicKey(String idToken) {
        try {
            // Step 1: Extract 'kid' from JWT header
            String kid = extractKidFromToken(idToken);

            // Step 2: Fetch JWKS from Apple
            ResponseEntity<String> response = restTemplate.getForEntity(JWKS_URL, String.class);
            if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
                throw new IllegalStateException("Failed to fetch Apple JWKS");
            }

            // Step 3: Parse JWKS and find matching key
            JsonNode jwksNode = objectMapper.readTree(response.getBody());
            JsonNode keysNode = jwksNode.get("keys");
            if (keysNode == null || !keysNode.isArray()) {
                throw new IllegalStateException("Invalid JWKS format from Apple");
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
            log.error("Failed to get Apple public key", e);
            throw new IllegalArgumentException("Failed to verify Apple ID token");
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
     * Apple uses RSA-256 for signing ID tokens
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
            throw new IllegalArgumentException("Failed to build Apple public key");
        }
    }
}