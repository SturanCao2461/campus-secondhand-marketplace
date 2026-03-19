package com.campus.marketplace.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Favorite entity — tracks which listings a user has saved.
 */
@Entity
@Table(name = "favorites", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "listing_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class Favorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
