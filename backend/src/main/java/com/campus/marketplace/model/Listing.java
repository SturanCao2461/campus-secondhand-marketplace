package com.campus.marketplace.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Listing entity — represents an item for sale on campus.
 * No payment or delivery logic: local in-person pickup only.
 * TODO: Add image URL list.
 * TODO: Add campus location/building field.
 */
@Entity
@Table(name = "listings")
@Getter
@Setter
@NoArgsConstructor
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Condition condition;

    @Column
    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ListingStatus status = ListingStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    // TODO: Add images (List<String> imageUrls)
    // TODO: Add campus location field

    public enum Condition {
        NEW, LIKE_NEW, GOOD, FAIR, POOR
    }

    public enum ListingStatus {
        ACTIVE, SOLD, REMOVED
    }
}
