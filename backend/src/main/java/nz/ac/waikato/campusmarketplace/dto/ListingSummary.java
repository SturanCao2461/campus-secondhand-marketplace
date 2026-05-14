package nz.ac.waikato.campusmarketplace.dto;

import nz.ac.waikato.campusmarketplace.entity.Listing;
import nz.ac.waikato.campusmarketplace.entity.ListingStatus;
import nz.ac.waikato.campusmarketplace.entity.ListingType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ListingSummary(
        Long id,
        String title,
        BigDecimal price,
        String imageUrl,
        ListingStatus status,
        ListingType listingType,
        CategoryResponse category,
        LocalDateTime createdAt
) {
    public static ListingSummary from(Listing l) {
        return new ListingSummary(
                l.getId(),
                l.getTitle(),
                l.getPrice(),
                "/api/uploads/" + l.getImagePath(),
                l.getStatus(),
                l.getListingType(),
                CategoryResponse.from(l.getCategory()),
                l.getCreatedAt()
        );
    }
}
