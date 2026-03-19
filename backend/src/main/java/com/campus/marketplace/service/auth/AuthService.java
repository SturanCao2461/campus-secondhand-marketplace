package com.campus.marketplace.service.auth;

import org.springframework.stereotype.Service;

/**
 * Authentication service.
 * TODO: Implement user registration, login, and JWT token generation.
 * TODO: Validate university email domain (e.g., @university.edu).
 */
@Service
public class AuthService {

    public void register(String email, String password, String name) {
        // TODO: Check email uniqueness, hash password, persist User entity
        throw new UnsupportedOperationException("Not yet implemented");
    }

    public String login(String email, String password) {
        // TODO: Validate credentials, return JWT token
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
