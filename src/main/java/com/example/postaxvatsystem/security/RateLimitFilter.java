package com.example.postaxvatsystem.security;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Order(1)
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String key = getClientKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, k -> createNewBucket());

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
        } else {
            long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
            response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.getWriter().write("{\"error\":\"Too many requests\",\"retryAfter\":" + waitForRefill + "}");
            log.warn("Rate limit exceeded for client: {}", key);
        }
    }

    private String getClientKey(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");
        return ip + ":" + (userAgent != null ? userAgent : "unknown");
    }

    private Bucket createNewBucket() {
        return io.github.bucket4j.Bucket.builder()
                .addLimit(io.github.bucket4j.Bandwidth.classic(100, 
                    io.github.bucket4j.Refill.intervally(100, java.time.Duration.ofMinutes(1))))
                .addLimit(io.github.bucket4j.Bandwidth.classic(1000, 
                    io.github.bucket4j.Refill.intervally(1000, java.time.Duration.ofHours(1))))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip rate limiting for health checks and static resources
        return path.startsWith("/actuator") || path.startsWith("/error");
    }
}
