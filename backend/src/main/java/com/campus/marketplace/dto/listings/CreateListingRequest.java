package com.campus.marketplace.dto.listings;

import com.campus.marketplace.model.Listing;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CreateListingRequest {
    @NotBlank
    private String title;

    private String description;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = true) // 0.0 is allowed for free giveaways
    private BigDecimal price;

    @NotNull
    private Listing.Condition condition;

    private String category;
    // TODO: Add imageUrls field
    // TODO: Add campus location field
}
