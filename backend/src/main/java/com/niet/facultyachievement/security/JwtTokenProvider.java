package com.niet.facultyachievement.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider {

    // Default 256-bit secret key for local development
    private static final String DEFAULT_SECRET = "NIETFacultyAchievementPortalSecretKey2026MustBeAtLeast256BitsLong!";
    
    @Value("${app.jwt.secret:" + DEFAULT_SECRET + "}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms:86400000}") // 24 Hours
    private long jwtExpirationInMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
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
