package nz.ac.waikato.campusmarketplace.dto;

import nz.ac.waikato.campusmarketplace.entity.ListingType;

import java.math.BigDecimal;

public record BrowseQuery(
        String keyword,
        String categoryCode,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        ListingType listingType
) {}
