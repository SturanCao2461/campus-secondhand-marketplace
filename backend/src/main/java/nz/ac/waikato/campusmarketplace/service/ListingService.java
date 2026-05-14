package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.dto.CreateListingRequest;
import nz.ac.waikato.campusmarketplace.dto.ListingResponse;
import nz.ac.waikato.campusmarketplace.entity.Category;
import nz.ac.waikato.campusmarketplace.entity.Listing;
import nz.ac.waikato.campusmarketplace.entity.ListingStatus;
import nz.ac.waikato.campusmarketplace.entity.ListingType;
import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import nz.ac.waikato.campusmarketplace.repository.CategoryRepository;
import nz.ac.waikato.campusmarketplace.repository.ListingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ListingService {

    private final CategoryRepository categories;
    private final ListingRepository listings;

    public ListingService(CategoryRepository categories, ListingRepository listings) {
        this.categories = categories;
        this.listings = listings;
    }

    @Transactional
    public ListingResponse create(User currentUser, CreateListingRequest req, String imagePath) {
        Category category = categories.findByCode(req.categoryCode())
                .filter(Category::getActive)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CATEGORY,
                        "Category is not available."));

        if (req.listingType() == ListingType.SELL) {
            if (req.price() == null || req.price().signum() <= 0) {
                throw new ApiException(ErrorCode.INVALID_PRICE,
                        "Selling listings must have a positive price.");
            }
        }
        if (req.originalPrice() != null && req.originalPrice().signum() <= 0) {
            throw new ApiException(ErrorCode.INVALID_PRICE,
                    "Original price must be positive when supplied.");
        }

        BigDecimal effectivePrice = req.listingType() == ListingType.GIVEAWAY ? null : req.price();
        boolean effectiveNegotiable = req.negotiable() != null && req.negotiable();

        Listing draft = Listing.builder()
                .owner(currentUser)
                .category(category)
                .title(req.title())
                .description(req.description())
                .price(effectivePrice)
                .originalPrice(req.originalPrice())
                .imagePath(imagePath)
                .status(ListingStatus.AVAILABLE)
                .listingType(req.listingType())
                .condition(req.condition())
                .meetAt(req.meetAt())
                .negotiable(effectiveNegotiable)
                .reasonForSelling(req.reasonForSelling())
                .build();

        Listing saved = listings.save(draft);
        return ListingResponse.from(saved);
    }
}
