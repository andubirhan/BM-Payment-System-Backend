package com.example.postaxvatsystem.controller;

import com.example.postaxvatsystem.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final JwtTokenProvider tokenProvider;

    // Demo users for testing - in production, this should be replaced with database authentication
    private static final Map<String, DemoUser> DEMO_USERS = Map.of(
            "admin", new DemoUser("admin", "admin123", "ADMIN"),
            "cashier", new DemoUser("cashier", "cashier123", "CASHIER"),
            "manager", new DemoUser("manager", "manager123", "MANAGER")
    );

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.username());

        DemoUser user = DEMO_USERS.get(request.username());
        
        if (user == null || !user.password().equals(request.password())) {
            log.warn("Invalid credentials for user: {}", request.username());
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Invalid credentials");
            error.put("message", "Username or password is incorrect");
            return ResponseEntity.status(401).body(error);
        }

        String token = tokenProvider.generateToken(user.username(), user.role());
        
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("username", user.username());
        response.put("role", user.role());
        response.put("type", "Bearer");

        log.info("User {} logged in successfully with role: {}", user.username(), user.role());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(Map.of("valid", false));
        }

        String token = authHeader.substring(7);
        try {
            Claims claims = tokenProvider.getClaimsFromToken(token);
            return ResponseEntity.ok(Map.of(
                    "valid", true,
                    "username", claims.getSubject(),
                    "role", claims.get("role", String.class)
            ));
        } catch (JwtException | IllegalArgumentException e) {
            return ResponseEntity.status(401).body(Map.of("valid", false));
        }
    }

    record LoginRequest(String username, String password) {}
    record DemoUser(String username, String password, String role) {}
}
