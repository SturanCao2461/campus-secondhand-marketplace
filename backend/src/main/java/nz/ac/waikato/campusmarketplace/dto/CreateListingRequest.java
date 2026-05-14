package nz.ac.waikato.campusmarketplace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import nz.ac.waikato.campusmarketplace.entity.Condition;
import nz.ac.waikato.campusmarketplace.entity.ListingType;

import java.math.BigDecimal;

public record CreateListingRequest(
        @NotBlank @Size(max = 80) String title,
        @NotBlank @Size(max = 2000) String description,
        @NotBlank String categoryCode,
        @NotNull ListingType listingType,
        BigDecimal price,
        BigDecimal originalPrice,
        Condition condition,
        @Size(max = 100) String meetAt,
        Boolean negotiable,
        @Size(max = 100) String reasonForSelling
) {}
