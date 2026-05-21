package nz.ac.waikato.campusmarketplace.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "conversations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_conversations_listing_buyer",
                columnNames = {"listing_id", "buyer_id"}
        ),
        indexes = {
                @Index(name = "idx_conversations_buyer", columnList = "buyer_id"),
                @Index(name = "idx_conversations_seller", columnList = "seller_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_conversations_listing"))
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_conversations_buyer"))
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_conversations_seller"))
    private User seller;

    @Column(name = "buyer_last_read_at")
    private LocalDateTime buyerLastReadAt;

    @Column(name = "seller_last_read_at")
    private LocalDateTime sellerLastReadAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void onPrePersist() {
        updatedAt = LocalDateTime.now();
    }
}
