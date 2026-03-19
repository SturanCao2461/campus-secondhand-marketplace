package com.campus.marketplace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Application security configuration.
 * <p>
 * This is a stateless REST API. Sessions are never created; authentication will
 * use JWT Bearer tokens in the Authorization header (not cookies), so CSRF
 * protection is not applicable and is intentionally disabled.
 * <p>
 * TODO: Replace permitAll with role-based access control once JWT auth is implemented.
 * TODO: Add a JWT authentication filter before deploying to production.
 */
@Configuration
@EnableWebSecurity
public class AppConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Stateless API using JWT Bearer tokens — CSRF not applicable (no session cookies).
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // TODO: Replace permitAll with proper role-based access control
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
