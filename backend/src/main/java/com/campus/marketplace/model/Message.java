package com.campus.marketplace.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Message entity — simple in-app messaging between buyer and seller.
 * TODO: Add conversation grouping (add Conversation entity).
 * TODO: Add read/unread status.
 */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(name = "listing_id")
    private Long listingId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // TODO: Add isRead flag
    // TODO: Add Conversation entity to group messages
}
