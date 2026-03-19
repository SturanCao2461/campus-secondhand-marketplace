package com.campus.marketplace.dto.listings;

import com.campus.marketplace.model.Listing;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class ListingDto {
    private Long id;
    private String title;
    private String description;
    private BigDecimal price;
    private Listing.Condition condition;
    private String category;
    private Long sellerId;
    private Listing.ListingStatus status;
    private Instant createdAt;
    // TODO: Add seller name, avatar
    // TODO: Add image URLs
}
