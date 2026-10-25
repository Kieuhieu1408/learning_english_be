package com.learning.english.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private static final String TYPE_CLAIM = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final long jwtExpirationInMs;
    private final long refreshExpirationInMs;

    public JwtTokenProvider(
            @Value("${app.jwt.secret:SuperSecretKeyMustBeAtLeast256BitsLongForHMACSHA256}") String jwtSecret,
            @Value("${app.jwt.expiration-ms:900000}") long jwtExpirationInMs,
            @Value("${app.jwt.refresh-expiration-ms:604800000}") long refreshExpirationInMs) {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpirationInMs = jwtExpirationInMs;
        this.refreshExpirationInMs = refreshExpirationInMs;
    }

    public long getAccessExpirationSeconds() {
        return jwtExpirationInMs / 1000;
    }

    public String generateToken(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        return generateAccessToken(userPrincipal);
    }

    public String generateAccessToken(UserPrincipal userPrincipal) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim("userId", userPrincipal.getId().toString())
                .claim("role", userPrincipal.getAuthorities().iterator().next().getAuthority())
                .claim(TYPE_CLAIM, TYPE_ACCESS)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationInMs;
    }

    /** @param tokenId unique id stored as the JWT "jti" so the token can be revoked server-side. */
    public String generateRefreshToken(UserPrincipal userPrincipal, UUID tokenId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + refreshExpirationInMs);

        return Jwts.builder()
                .id(tokenId.toString())
                .subject(userPrincipal.getUsername())
                .claim("userId", userPrincipal.getId().toString())
                .claim(TYPE_CLAIM, TYPE_REFRESH)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public String getUsernameFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    public UUID getTokenIdFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return UUID.fromString(claims.getId());
    }

    public UUID getUserIdFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return UUID.fromString(claims.get("userId", String.class));
    }

    /** Validates an access token (refresh tokens are rejected). */
    public boolean validateToken(String authToken) {
        return validateTokenOfType(authToken, TYPE_ACCESS);
    }

    /** Validates a refresh token (access tokens are rejected). */
    public boolean validateRefreshToken(String refreshToken) {
        return validateTokenOfType(refreshToken, TYPE_REFRESH);
    }

    private boolean validateTokenOfType(String authToken, String expectedType) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(authToken).getPayload();
            return expectedType.equals(claims.get(TYPE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException ex) {
            System.err.println("JWT Token validation failed: " + ex.getMessage());
            ex.printStackTrace();
        }
        return false;
    }
}
