package nz.ac.waikato.campusmarketplace.dto;

import jakarta.validation.constraints.NotNull;
import nz.ac.waikato.campusmarketplace.entity.ListingStatus;

public record ChangeStatusRequest(
        @NotNull ListingStatus newStatus
) {}
