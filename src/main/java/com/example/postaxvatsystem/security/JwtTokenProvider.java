package com.example.postaxvatsystem.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpirationInMs;

    private volatile SecretKey signingKey;
    private volatile JwtParser jwtParser;

    private SecretKey getSigningKey() {
        SecretKey key = signingKey;
        if (key == null) {
            synchronized (this) {
                key = signingKey;
                if (key == null) {
                    byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
                    key = Keys.hmacShaKeyFor(keyBytes);
                    signingKey = key;
                }
            }
        }
        return key;
    }

    private JwtParser getJwtParser() {
        JwtParser parser = jwtParser;
        if (parser == null) {
            synchronized (this) {
                parser = jwtParser;
                if (parser == null) {
                    parser = Jwts.parser().verifyWith(getSigningKey()).build();
                    jwtParser = parser;
                }
            }
        }
        return parser;
    }

    public Claims getClaimsFromToken(String token) {
        return getJwtParser().parseSignedClaims(token).getPayload();
    }

    public String generateToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationInMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    public String getRoleFromToken(String token) {
        return getClaimsFromToken(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            getClaimsFromToken(token);
            return true;
        } catch (SecurityException ex) {
            log.error("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.warn("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty: {}", ex.getMessage());
        }
        return false;
    }
}
