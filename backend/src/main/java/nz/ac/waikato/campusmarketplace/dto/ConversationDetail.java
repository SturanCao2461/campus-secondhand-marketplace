package nz.ac.waikato.campusmarketplace.dto;

import java.time.LocalDateTime;

public record ConversationDetail(
        Long id,
        Long listingId,
        String listingTitle,
        String listingImageUrl,
        Long counterpartId,
        String counterpartNickname,
        LocalDateTime createdAt
) {}
