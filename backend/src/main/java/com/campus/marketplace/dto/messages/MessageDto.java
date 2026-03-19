package com.campus.marketplace.dto.messages;

import lombok.Data;

import java.time.Instant;

@Data
public class MessageDto {
    private Long id;
    private Long senderId;
    private Long receiverId;
    private Long listingId;
    private String content;
    private Instant createdAt;
    // TODO: Add read status
    // TODO: Add sender name
}
