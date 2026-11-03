package com.example.postaxvatsystem.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    @Test
    void parsesAndValidatesTokenClaims() {
        JwtTokenProvider provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", "test-jwt-secret-key-that-is-at-least-32-bytes");
        ReflectionTestUtils.setField(provider, "jwtExpirationInMs", 60_000L);

        String token = provider.generateToken("test-user", "MANAGER");

        assertTrue(provider.validateToken(token));
        assertEquals("test-user", provider.getClaimsFromToken(token).getSubject());
        assertEquals("MANAGER", provider.getClaimsFromToken(token).get("role", String.class));
    }
}
