package com.campus.marketplace.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Application security configuration.
 * TODO: Replace permissive config with JWT-based authentication.
 * TODO: Restrict endpoints to authenticated users as features are built.
 */
@Configuration
@EnableWebSecurity
public class AppConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF disabled: this is a stateless REST API that will use JWT Bearer tokens.
            // CSRF protection is not needed for token-based auth (no cookies).
            // TODO: Ensure JWT auth is in place before deploying to production.
            .csrf(csrf -> csrf.disable())
            // TODO: Replace permitAll with proper role-based access control
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }
}
