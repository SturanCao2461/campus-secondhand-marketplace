package nz.ac.waikato.campusmarketplace.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "listings",
        indexes = {
                @Index(name = "idx_listings_owner",   columnList = "owner_id"),
                @Index(name = "idx_listings_status",  columnList = "status"),
                @Index(name = "idx_listings_created", columnList = "created_at"),
                @Index(name = "idx_listings_image",   columnList = "image_path")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_listings_owner"))
    private User owner;

    @Column(nullable = false, length = 80)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "original_price", precision = 10, scale = 2)
    private BigDecimal originalPrice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_listings_category"))
    private Category category;

    @Column(name = "image_path", nullable = false, length = 255)
    private String imagePath;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ListingStatus status = ListingStatus.AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_type", nullable = false, length = 20)
    @Builder.Default
    private ListingType listingType = ListingType.SELL;

    @Enumerated(EnumType.STRING)
    @Column(name = "`condition`", length = 20)
    private Condition condition;

    @Column(name = "meet_at", length = 100)
    private String meetAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean negotiable = false;

    @Column(name = "reason_for_selling", length = 100)
    private String reasonForSelling;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
