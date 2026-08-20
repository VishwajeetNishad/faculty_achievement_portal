package com.niet.facultyachievement.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    // HS256 requires a signing key of at least 256 bits (32 bytes).
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${app.jwt.secret:}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms:86400000}") // 24 Hours
    private long jwtExpirationInMs;

    private SecretKey signingKey;

    /**
     * Validates the configured signing secret at startup and fails fast if it is
     * missing or too weak, so the application never falls back to a built-in key.
     */
    @PostConstruct
    void init() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT signing secret is not configured. Set the JWT_SECRET environment variable "
                    + "(or app.jwt.secret) to a random key of at least 256 bits (32+ characters).");
        }
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT signing secret is too short (" + keyBytes.length + " bytes). "
                    + "HS256 requires at least " + MIN_SECRET_BYTES + " bytes (256 bits).");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    public String generateToken(Authentication authentication, Long userId, String role, Long departmentId) {
        String email = authentication.getName();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .claim("departmentId", departmentId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Helper method for security verification tests: generates an expired JWT token.
     */
    public String generateExpiredTokenForTesting(String email, Long userId, String role, Long departmentId) {
        Date now = new Date();
        Date pastExpiryDate = new Date(now.getTime() - 3600000); // 1 Hour in the past

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role)
                .claim("departmentId", departmentId)
                .issuedAt(new Date(now.getTime() - 7200000))
                .expiration(pastExpiryDate)
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    public String getEmailFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
