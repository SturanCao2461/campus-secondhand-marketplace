package com.campus.marketplace.controller.auth;

import com.campus.marketplace.dto.auth.LoginRequest;
import com.campus.marketplace.dto.auth.RegisterRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication controller.
 * TODO: Implement JWT-based login and registration.
 * TODO: Validate university email domain on registration.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@RequestBody RegisterRequest request) {
        // TODO: Validate input, check email domain, hash password, save user
        return ResponseEntity.ok(Map.of("message", "Registration placeholder — not yet implemented"));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
        // TODO: Authenticate user, generate and return JWT token
        return ResponseEntity.ok(Map.of("message", "Login placeholder — not yet implemented"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        // TODO: Invalidate JWT token / session
        return ResponseEntity.ok(Map.of("message", "Logout placeholder — not yet implemented"));
    }
}
