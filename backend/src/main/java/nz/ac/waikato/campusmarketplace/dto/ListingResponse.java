package nz.ac.waikato.campusmarketplace.dto;

import nz.ac.waikato.campusmarketplace.entity.Condition;
import nz.ac.waikato.campusmarketplace.entity.Listing;
import nz.ac.waikato.campusmarketplace.entity.ListingStatus;
import nz.ac.waikato.campusmarketplace.entity.ListingType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ListingResponse(
        Long id,
        Long ownerId,
        String title,
        String description,
        BigDecimal price,
        BigDecimal originalPrice,
        CategoryResponse category,
        String imageUrl,
        ListingStatus status,
        ListingType listingType,
        Condition condition,
        String meetAt,
        Boolean negotiable,
        String reasonForSelling,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ListingResponse from(Listing l) {
        return new ListingResponse(
                l.getId(),
                l.getOwner().getId(),
                l.getTitle(),
                l.getDescription(),
                l.getPrice(),
                l.getOriginalPrice(),
                CategoryResponse.from(l.getCategory()),
                "/api/uploads/" + l.getImagePath(),
                l.getStatus(),
                l.getListingType(),
                l.getCondition(),
                l.getMeetAt(),
                l.getNegotiable(),
                l.getReasonForSelling(),
                l.getCreatedAt(),
                l.getUpdatedAt()
        );
    }
}
