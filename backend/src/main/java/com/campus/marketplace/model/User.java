package com.campus.marketplace.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * User entity — represents a campus student, staff, or employee.
 * TODO: Add university email domain validation.
 * TODO: Add profile picture field (URL only, no binary storage).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.STUDENT;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // TODO: Add phone number (optional)
    // TODO: Add avatar URL
    // TODO: Add rating/review fields later

    public enum UserRole {
        STUDENT, STAFF, EMPLOYEE
    }
}
