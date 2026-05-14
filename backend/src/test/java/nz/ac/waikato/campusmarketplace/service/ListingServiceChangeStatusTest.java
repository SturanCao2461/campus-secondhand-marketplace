package nz.ac.waikato.campusmarketplace.service;

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

class ListingServiceChangeStatusTest {

    private static final Long LISTING_ID = 42L;

    CategoryRepository categories;
    ListingRepository listings;
    ListingService svc;

    User owner;
    User otherUser;
    Category booksActive;

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

        when(listings.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Listing fixtureWithStatus(ListingStatus status) {
        return Listing.builder()
                .id(LISTING_ID)
                .owner(owner)
                .category(booksActive)
                .title("Item")
                .description("Test")
                .price(new BigDecimal("10.00"))
                .imagePath("listings/x.jpg")
                .status(status)
                .listingType(ListingType.SELL)
                .build();
    }

    private Listing assertSavedWithStatus(ListingStatus expected) {
        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listings).save(captor.capture());
        Listing saved = captor.getValue();
        assertEquals(expected, saved.getStatus());
        return saved;
    }

    @Test
    void availableToReservedSucceeds() {
        when(listings.findById(LISTING_ID)).thenReturn(Optional.of(fixtureWithStatus(ListingStatus.AVAILABLE)));

        ListingResponse r = svc.changeStatus(owner, LISTING_ID, ListingStatus.RESERVED);

        assertSavedWithStatus(ListingStatus.RESERVED);
        assertEquals(ListingStatus.RESERVED, r.status());
    }

    @Test
    void reservedToSoldSucceeds() {
        when(listings.findById(LISTING_ID)).thenReturn(Optional.of(fixtureWithStatus(ListingStatus.RESERVED)));

        svc.changeStatus(owner, LISTING_ID, ListingStatus.SOLD);

        assertSavedWithStatus(ListingStatus.SOLD);
    }

    @Test
    void soldToAvailableSucceedsBecauseFsmIsReversible() {
        // spec DC-3 specifically calls this out: SOLD can revert to AVAILABLE.
        when(listings.findById(LISTING_ID)).thenReturn(Optional.of(fixtureWithStatus(ListingStatus.SOLD)));

        svc.changeStatus(owner, LISTING_ID, ListingStatus.AVAILABLE);

        assertSavedWithStatus(ListingStatus.AVAILABLE);
    }

    @Test
    void availableToRemovedSucceeds() {
        when(listings.findById(LISTING_ID)).thenReturn(Optional.of(fixtureWithStatus(ListingStatus.AVAILABLE)));

        svc.changeStatus(owner, LISTING_ID, ListingStatus.REMOVED);

        assertSavedWithStatus(ListingStatus.REMOVED);
    }

    @Test
    void unknownListingThrowsListingNotFound() {
        when(listings.findById(LISTING_ID)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.changeStatus(owner, LISTING_ID, ListingStatus.RESERVED));

        assertEquals(ErrorCode.LISTING_NOT_FOUND, ex.getCode());
        verify(listings, never()).save(any());
    }

    @Test
    void nonOwnerThrowsListingNotFoundForAntiEnumeration() {
        when(listings.findById(LISTING_ID)).thenReturn(Optional.of(fixtureWithStatus(ListingStatus.AVAILABLE)));

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.changeStatus(otherUser, LISTING_ID, ListingStatus.RESERVED));

        // spec §7.3: non-owner is hidden behind LISTING_NOT_FOUND, not NOT_LISTING_OWNER.
        assertEquals(ErrorCode.LISTING_NOT_FOUND, ex.getCode());
        verify(listings, never()).save(any());
    }

    @Test
    void removedToAvailableThrowsInvalidStatusTransition() {
        when(listings.findById(LISTING_ID)).thenReturn(Optional.of(fixtureWithStatus(ListingStatus.REMOVED)));

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.changeStatus(owner, LISTING_ID, ListingStatus.AVAILABLE));

        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.getCode());
        verify(listings, never()).save(any());
    }

    @Test
    void selfTransitionThrowsInvalidStatusTransition() {
        when(listings.findById(LISTING_ID)).thenReturn(Optional.of(fixtureWithStatus(ListingStatus.AVAILABLE)));

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.changeStatus(owner, LISTING_ID, ListingStatus.AVAILABLE));

        assertEquals(ErrorCode.INVALID_STATUS_TRANSITION, ex.getCode());
        verify(listings, never()).save(any());
    }
}
