package nz.ac.waikato.campusmarketplace.repository;

import nz.ac.waikato.campusmarketplace.entity.Category;
import nz.ac.waikato.campusmarketplace.entity.Listing;
import nz.ac.waikato.campusmarketplace.entity.ListingStatus;
import nz.ac.waikato.campusmarketplace.entity.ListingType;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

public final class ListingSpecifications {

    private ListingSpecifications() {}

    public static Specification<Listing> hasStatusIn(List<ListingStatus> statuses) {
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Listing> titleContains(String keyword) {
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("title")), "%" + keyword.toLowerCase() + "%");
    }

    public static Specification<Listing> hasCategory(Category category) {
        return (root, query, cb) -> cb.equal(root.get("category"), category);
    }

    public static Specification<Listing> priceBetween(BigDecimal min, BigDecimal max) {
        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("price"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("price"), min);
            } else {
                return cb.lessThanOrEqualTo(root.get("price"), max);
            }
        };
    }

    public static Specification<Listing> hasListingType(ListingType type) {
        return (root, query, cb) -> cb.equal(root.get("listingType"), type);
    }
}
