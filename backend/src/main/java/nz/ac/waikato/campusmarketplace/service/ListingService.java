package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.dto.BrowseQuery;
import nz.ac.waikato.campusmarketplace.dto.CreateListingRequest;
import nz.ac.waikato.campusmarketplace.dto.ListingResponse;
import nz.ac.waikato.campusmarketplace.dto.PagedListings;
import nz.ac.waikato.campusmarketplace.dto.UpdateListingRequest;
import nz.ac.waikato.campusmarketplace.entity.Category;
import nz.ac.waikato.campusmarketplace.entity.Listing;
import nz.ac.waikato.campusmarketplace.entity.ListingStatus;
import nz.ac.waikato.campusmarketplace.entity.ListingType;
import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import nz.ac.waikato.campusmarketplace.repository.CategoryRepository;
import nz.ac.waikato.campusmarketplace.repository.ListingRepository;
import nz.ac.waikato.campusmarketplace.repository.ListingSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ListingService {

    private static final Map<ListingStatus, Set<ListingStatus>> ALLOWED_TRANSITIONS = Map.of(
            ListingStatus.AVAILABLE, Set.of(ListingStatus.RESERVED, ListingStatus.SOLD, ListingStatus.REMOVED),
            ListingStatus.RESERVED,  Set.of(ListingStatus.AVAILABLE, ListingStatus.SOLD, ListingStatus.REMOVED),
            ListingStatus.SOLD,      Set.of(ListingStatus.AVAILABLE, ListingStatus.REMOVED),
            ListingStatus.REMOVED,   Set.of()
    );

    private final CategoryRepository categories;
    private final ListingRepository listings;

    public ListingService(CategoryRepository categories, ListingRepository listings) {
        this.categories = categories;
        this.listings = listings;
    }

    @Transactional
    public ListingResponse create(User currentUser, CreateListingRequest req, String imagePath) {
        Category category = resolveActiveCategory(req.categoryCode());
        validatePrice(req.listingType(), req.price(), req.originalPrice());

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

    @Transactional
    public ListingResponse update(User currentUser, Long id, UpdateListingRequest req, String newImagePath) {
        Listing listing = listings.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.LISTING_NOT_FOUND,
                        "Listing not found."));

        // spec §7.3 / §7.4: non-owner is hidden as LISTING_NOT_FOUND, not NOT_LISTING_OWNER.
        if (!listing.getOwner().getId().equals(currentUser.getId())) {
            throw new ApiException(ErrorCode.LISTING_NOT_FOUND, "Listing not found.");
        }
        if (listing.getStatus() == ListingStatus.REMOVED) {
            throw new ApiException(ErrorCode.LISTING_REMOVED,
                    "This listing has been removed and cannot be edited.");
        }

        Category category = resolveActiveCategory(req.categoryCode());
        validatePrice(req.listingType(), req.price(), req.originalPrice());

        BigDecimal effectivePrice = req.listingType() == ListingType.GIVEAWAY ? null : req.price();
        boolean effectiveNegotiable = req.negotiable() != null && req.negotiable();

        listing.setTitle(req.title());
        listing.setDescription(req.description());
        listing.setCategory(category);
        listing.setListingType(req.listingType());
        listing.setPrice(effectivePrice);
        listing.setOriginalPrice(req.originalPrice());
        listing.setCondition(req.condition());
        listing.setMeetAt(req.meetAt());
        listing.setNegotiable(effectiveNegotiable);
        listing.setReasonForSelling(req.reasonForSelling());
        if (newImagePath != null) {
            listing.setImagePath(newImagePath);
        }

        Listing saved = listings.save(listing);
        return ListingResponse.from(saved);
    }

    @Transactional
    public ListingResponse changeStatus(User currentUser, Long id, ListingStatus newStatus) {
        Listing listing = listings.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.LISTING_NOT_FOUND,
                        "Listing not found."));

        // spec §7.3 / §7.4: non-owner is hidden as LISTING_NOT_FOUND, not NOT_LISTING_OWNER.
        if (!listing.getOwner().getId().equals(currentUser.getId())) {
            throw new ApiException(ErrorCode.LISTING_NOT_FOUND, "Listing not found.");
        }

        Set<ListingStatus> allowed = ALLOWED_TRANSITIONS.get(listing.getStatus());
        if (allowed == null || !allowed.contains(newStatus)) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cannot change status from " + listing.getStatus() + " to " + newStatus + ".");
        }

        listing.setStatus(newStatus);
        Listing saved = listings.save(listing);
        return ListingResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PagedListings browse(BrowseQuery query, Pageable pageable) {
        List<ListingStatus> publicStatuses = List.of(
                ListingStatus.AVAILABLE, ListingStatus.RESERVED, ListingStatus.SOLD);

        Specification<Listing> spec = ListingSpecifications.hasStatusIn(publicStatuses);

        if (query.keyword() != null && !query.keyword().isBlank()) {
            spec = spec.and(ListingSpecifications.titleContains(query.keyword().trim()));
        }
        if (query.categoryCode() != null && !query.categoryCode().isBlank()) {
            Category cat = categories.findByCode(query.categoryCode())
                    .filter(Category::getActive)
                    .orElse(null);
            if (cat != null) {
                spec = spec.and(ListingSpecifications.hasCategory(cat));
            }
        }
        if (query.minPrice() != null || query.maxPrice() != null) {
            spec = spec.and(ListingSpecifications.priceBetween(query.minPrice(), query.maxPrice()));
        }
        if (query.listingType() != null) {
            spec = spec.and(ListingSpecifications.hasListingType(query.listingType()));
        }

        Page<Listing> page = listings.findAll(spec, pageable);
        return PagedListings.from(page);
    }

    @Transactional(readOnly = true)
    public ListingResponse getDetail(Long id) {
        Listing listing = listings.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.LISTING_NOT_FOUND,
                        "Listing not found."));
        if (listing.getStatus() == ListingStatus.REMOVED) {
            throw new ApiException(ErrorCode.LISTING_NOT_FOUND, "Listing not found.");
        }
        return ListingResponse.from(listing);
    }

    @Transactional(readOnly = true)
    public PagedListings listMine(User currentUser, Pageable pageable,
                                  ListingStatus statusFilter, boolean includeRemoved) {
        Page<Listing> page;
        if (statusFilter != null) {
            page = listings.findByOwnerAndStatus(currentUser, statusFilter, pageable);
        } else if (!includeRemoved) {
            page = listings.findByOwnerAndStatusNot(currentUser, ListingStatus.REMOVED, pageable);
        } else {
            page = listings.findByOwner(currentUser, pageable);
        }
        return PagedListings.from(page);
    }

    @Transactional(readOnly = true)
    public ListingResponse getOne(User currentUser, Long id) {
        Listing listing = listings.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.LISTING_NOT_FOUND,
                        "Listing not found."));

        boolean isOwner = listing.getOwner().getId().equals(currentUser.getId());
        // spec §4.3: REMOVED listings are hidden from non-owners (anti-enumeration).
        if (listing.getStatus() == ListingStatus.REMOVED && !isOwner) {
            throw new ApiException(ErrorCode.LISTING_NOT_FOUND, "Listing not found.");
        }

        return ListingResponse.from(listing);
    }

    @Transactional
    public void remove(User currentUser, Long id) {
        Listing listing = listings.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.LISTING_NOT_FOUND,
                        "Listing not found."));

        // spec §7.3: non-owner is hidden as LISTING_NOT_FOUND, not NOT_LISTING_OWNER.
        if (!listing.getOwner().getId().equals(currentUser.getId())) {
            throw new ApiException(ErrorCode.LISTING_NOT_FOUND, "Listing not found.");
        }

        // spec §4.6: DELETE is idempotent — already-REMOVED is a silent success.
        if (listing.getStatus() != ListingStatus.REMOVED) {
            listing.setStatus(ListingStatus.REMOVED);
            listings.save(listing);
        }
    }

    private Category resolveActiveCategory(String code) {
        return categories.findByCode(code)
                .filter(Category::getActive)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CATEGORY,
                        "Category is not available."));
    }

    private void validatePrice(ListingType type, BigDecimal price, BigDecimal originalPrice) {
        if (type == ListingType.SELL) {
            if (price == null || price.signum() <= 0) {
                throw new ApiException(ErrorCode.INVALID_PRICE,
                        "Selling listings must have a positive price.");
            }
        }
        if (originalPrice != null && originalPrice.signum() <= 0) {
            throw new ApiException(ErrorCode.INVALID_PRICE,
                    "Original price must be positive when supplied.");
        }
    }
}

