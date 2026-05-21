package nz.ac.waikato.campusmarketplace.dto;

import jakarta.validation.constraints.NotNull;

public record CreateConversationRequest(
        @NotNull Long listingId
) {}
