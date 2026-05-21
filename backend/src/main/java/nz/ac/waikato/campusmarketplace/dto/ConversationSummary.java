package nz.ac.waikato.campusmarketplace.dto;

import java.time.LocalDateTime;

public record ConversationSummary(
        Long id,
        Long listingId,
        String listingTitle,
        String listingImageUrl,
        Long counterpartId,
        String counterpartNickname,
        String lastMessagePreview,
        LocalDateTime lastMessageAt,
        long unreadCount
) {}
