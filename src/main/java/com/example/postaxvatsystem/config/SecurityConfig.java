package com.example.postaxvatsystem.config;

import com.example.postaxvatsystem.security.JwtAuthenticationFilter;
import com.example.postaxvatsystem.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, HandlerMappingIntrospector introspector) throws Exception {
        MvcRequestMatcher.Builder mvc = new MvcRequestMatcher.Builder(introspector);

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints - no authentication required
                        .requestMatchers(mvc.pattern("/error")).permitAll()
                        .requestMatchers(mvc.pattern("/api/v1/auth/**")).permitAll()
                        .requestMatchers(mvc.pattern("/api/v1/pos/transactions/purchase")).permitAll()
                        .requestMatchers(mvc.pattern("/api/v1/pos/transactions/preauth")).permitAll()
                        .requestMatchers(mvc.pattern("/api/v1/pos/transactions/{id}")).permitAll()
                        .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/v1/pos/bank-payments/execute"))
                        .hasAnyRole("CASHIER", "SENIOR_CASHIER", "MANAGER", "ADMIN")
                        .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/v1/pos/bank-payments/terminal-scenarios"))
                        .hasAnyRole("CASHIER", "SENIOR_CASHIER", "MANAGER", "ADMIN")
                        .requestMatchers(mvc.pattern("/api/v1/pos/bank-payments/**")).permitAll()
                        .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/v1/test-transaction-data/{transactionType}"))
                        .authenticated()
                        .requestMatchers(mvc.pattern(HttpMethod.PUT, "/api/v1/test-transaction-data/{transactionType}"))
                        .authenticated()
                        
                        // Cashier role - basic operations
                        .requestMatchers(mvc.pattern("/api/v1/pos/transactions/{id}/capture")).hasAnyRole("CASHIER", "SENIOR_CASHIER", "MANAGER", "ADMIN")
                        
                        // Senior Cashier role - pre-auth and voids
                        .requestMatchers(mvc.pattern("/api/v1/pos/transactions/{id}/reverse")).hasAnyRole("SENIOR_CASHIER", "MANAGER", "ADMIN")
                        
                        // Manager role - refunds and overrides
                        .requestMatchers(mvc.pattern("/api/v1/pos/transactions/{id}/refund")).hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(mvc.pattern("/api/v1/pos/transactions/pending")).hasAnyRole("MANAGER", "ADMIN")
                        
                        // Admin role - full access
                        .requestMatchers(mvc.pattern("/api/v1/pos/dashboard/**")).hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(mvc.pattern("/api/v1/pos/reports/**")).hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(mvc.pattern("/api/v1/pos/search/**")).hasAnyRole("CASHIER", "SENIOR_CASHIER", "MANAGER", "ADMIN")
                        .requestMatchers(mvc.pattern(HttpMethod.PUT, "/api/v1/pos/settings/currency")).hasRole("ADMIN")
                        .requestMatchers(mvc.pattern(HttpMethod.PUT, "/api/v1/pos/settings/pricing")).hasRole("ADMIN")
                        .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/v1/pos/settings/currency")).hasAnyRole("CASHIER", "SENIOR_CASHIER", "MANAGER", "ADMIN")
                        .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/v1/pos/settings/pricing")).hasAnyRole("CASHIER", "SENIOR_CASHIER", "MANAGER", "ADMIN")
                        
                        // Migration endpoints (admin only)
                        .requestMatchers(mvc.pattern("/api/v1/admin/migration/**")).hasRole("ADMIN")
                        .requestMatchers(mvc.pattern("/api/v1/admin/**")).hasRole("ADMIN")
                        
                        .anyRequest().authenticated()
                )
                .addFilterBefore(rateLimitFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Development: Allow all origins
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}