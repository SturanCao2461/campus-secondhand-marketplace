package nz.ac.waikato.campusmarketplace.dto;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderNickname,
        String content,
        LocalDateTime createdAt
) {}
