package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.dto.ListingResponse;
import nz.ac.waikato.campusmarketplace.dto.UpdateListingRequest;
import nz.ac.waikato.campusmarketplace.entity.Category;
import nz.ac.waikato.campusmarketplace.entity.Condition;
import nz.ac.waikato.campusmarketplace.entity.Listing;
import nz.ac.waikato.campusmarketplace.entity.ListingStatus;
import nz.ac.waikato.campusmarketplace.entity.ListingType;
import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import nz.ac.waikato.campusmarketplace.repository.CategoryRepository;
import nz.ac.waikato.campusmarketplace.repository.ListingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListingServiceUpdateTest {

    private static final Long LISTING_ID = 42L;
    private static final String OLD_IMAGE_PATH = "listings/old.jpg";
    private static final String NEW_IMAGE_PATH = "listings/new.jpg";

    CategoryRepository categories;
    ListingRepository listings;
    ListingService svc;

    User owner;
    User otherUser;
    Category booksActive;
    Category electronicsActive;
    Listing existing;

    @BeforeEach
    void setUp() {
        categories = mock(CategoryRepository.class);
        listings = mock(ListingRepository.class);
        svc = new ListingService(categories, listings);

        owner = User.builder().id(7L).email("o@x").nickname("Owner").build();
        otherUser = User.builder().id(8L).email("x@x").nickname("Other").build();
        booksActive = Category.builder()
                .id(1L).code("BOOKS").nameEn("Books").nameZh("书籍")
                .sortOrder(10).active(true).build();
        electronicsActive = Category.builder()
                .id(2L).code("ELECTRONICS").nameEn("Electronics").nameZh("电子")
                .sortOrder(20).active(true).build();

        existing = Listing.builder()
                .id(LISTING_ID)
                .owner(owner)
                .category(booksActive)
                .title("Original title")
                .description("Original description")
                .price(new BigDecimal("30.00"))
                .imagePath(OLD_IMAGE_PATH)
                .status(ListingStatus.AVAILABLE)
                .listingType(ListingType.SELL)
                .condition(Condition.GOOD)
                .negotiable(false)
                .build();

        when(listings.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private UpdateListingRequest sellRequest(String categoryCode, BigDecimal price) {
        return new UpdateListingRequest(
                "Edited title", "Edited description", categoryCode, ListingType.SELL,
                price, null, Condition.LIKE_NEW, "Hub", true, null);
    }

    @Test
    void happyPathWithoutNewImageKeepsOldImagePath() {
        when(listings.findById(LISTING_ID)).thenReturn(Optional.of(existing));
        when(categories.findByCode("ELECTRONICS")).thenReturn(Optional.of(electronicsActive));

        ListingResponse r = svc.update(owner, LISTING_ID,
                sellRequest("ELECTRONICS", new BigDecimal("99.00")), null);

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listings).save(captor.capture());
        Listing saved = captor.getValue();
        assertEquals("Edited title", saved.getTitle());
        assertEquals("Edited description", saved.getDescription());
        assertEquals(electronicsActive, saved.getCategory());
        assertEquals(0, new BigDecimal("99.00").compareTo(saved.getPrice()));
        assertEquals(Condition.LIKE_NEW, saved.getCondition());
        assertEquals("Hub", saved.getMeetAt());
        assertEquals(true, saved.getNegotiable());
        assertEquals(OLD_IMAGE_PATH, saved.getImagePath());
        assertEquals(LISTING_ID, r.id());
    }

    @Test
    void happyPathWithNewImagePathReplacesImagePath() {
        when(listings.findById(LISTING_ID)).thenReturn(Optional.of(existing));
        when(categories.findByCode("BOOKS")).thenReturn(Optional.of(booksActive));

        svc.update(owner, LISTING_ID,
                sellRequest("BOOKS", new BigDecimal("50.00")), NEW_IMAGE_PATH);

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listings).save(captor.capture());
        assertEquals(NEW_IMAGE_PATH, captor.getValue().getImagePath());
    }

    @Test
    void unknownListingThrowsListingNotFound() {
        when(listings.findById(LISTING_ID)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.update(owner, LISTING_ID,
                        sellRequest("BOOKS", new BigDecimal("10.00")), null));

        assertEquals(ErrorCode.LISTING_NOT_FOUND, ex.getCode());
        verify(listings, never()).save(any());
    }

    @Test
    void nonOwnerThrowsListingNotFoundForAntiEnumeration() {
        when(listings.findById(LISTING_ID)).thenReturn(Optional.of(existing));

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.update(otherUser, LISTING_ID,
                        sellRequest("BOOKS", new BigDecimal("10.00")), null));

        // spec §7.3: non-owner is hidden behind LISTING_NOT_FOUND, not NOT_LISTING_OWNER.
        assertEquals(ErrorCode.LISTING_NOT_FOUND, ex.getCode());
        verify(listings, never()).save(any());
    }

    @Test
    void editingRemovedListingThrowsListingRemoved() {
        existing.setStatus(ListingStatus.REMOVED);
        when(listings.findById(LISTING_ID)).thenReturn(Optional.of(existing));

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.update(owner, LISTING_ID,
                        sellRequest("BOOKS", new BigDecimal("10.00")), null));

        assertEquals(ErrorCode.LISTING_REMOVED, ex.getCode());
        verify(listings, never()).save(any());
    }

    @Test
    void unknownCategoryThrowsInvalidCategory() {
        when(listings.findById(LISTING_ID)).thenReturn(Optional.of(existing));
        when(categories.findByCode("MYSTERY")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.update(owner, LISTING_ID,
                        sellRequest("MYSTERY", new BigDecimal("10.00")), null));

        assertEquals(ErrorCode.INVALID_CATEGORY, ex.getCode());
        verify(listings, never()).save(any());
    }

    @Test
    void sellWithNullPriceThrowsInvalidPrice() {
        when(listings.findById(LISTING_ID)).thenReturn(Optional.of(existing));
        when(categories.findByCode("BOOKS")).thenReturn(Optional.of(booksActive));

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.update(owner, LISTING_ID,
                        sellRequest("BOOKS", null), null));

        assertEquals(ErrorCode.INVALID_PRICE, ex.getCode());
        verify(listings, never()).save(any());
    }
}
