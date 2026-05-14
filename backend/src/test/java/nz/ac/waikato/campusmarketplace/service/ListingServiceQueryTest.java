package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.dto.ListingResponse;
import nz.ac.waikato.campusmarketplace.dto.PagedListings;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListingServiceQueryTest {

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
    }

    private Listing fixtureWithStatus(User listingOwner, ListingStatus status) {
        return Listing.builder()
                .id(LISTING_ID)
                .owner(listingOwner)
                .category(booksActive)
                .title("Item")
                .description("Test")
                .price(new BigDecimal("10.00"))
                .imagePath("listings/x.jpg")
                .status(status)
                .listingType(ListingType.SELL)
                .build();
    }

    // ---------- listMine ----------

    @Test
    void listMineDefaultExcludesRemoved() {
        Pageable pageable = PageRequest.of(0, 12);
        Page<Listing> page = new PageImpl<>(List.of(fixtureWithStatus(owner, ListingStatus.AVAILABLE)),
                pageable, 1L);
        when(listings.findByOwnerAndStatusNot(owner, ListingStatus.REMOVED, pageable))
                .thenReturn(page);

        PagedListings result = svc.listMine(owner, pageable, null, false);

        verify(listings).findByOwnerAndStatusNot(owner, ListingStatus.REMOVED, pageable);
        verify(listings, never()).findByOwner(any(), any());
        verify(listings, never()).findByOwnerAndStatus(any(), any(), any());
        assertEquals(1L, result.totalItems());
    }

    @Test
    void listMineWithStatusFilterUsesExactStatusQuery() {
        Pageable pageable = PageRequest.of(0, 12);
        Page<Listing> page = new PageImpl<>(List.of(fixtureWithStatus(owner, ListingStatus.AVAILABLE)),
                pageable, 1L);
        when(listings.findByOwnerAndStatus(owner, ListingStatus.AVAILABLE, pageable))
                .thenReturn(page);

        svc.listMine(owner, pageable, ListingStatus.AVAILABLE, false);

        verify(listings).findByOwnerAndStatus(owner, ListingStatus.AVAILABLE, pageable);
        verify(listings, never()).findByOwner(any(), any());
        verify(listings, never()).findByOwnerAndStatusNot(any(), any(), any());
    }

    @Test
    void listMineIncludeRemovedUsesFindByOwner() {
        Pageable pageable = PageRequest.of(0, 12);
        Page<Listing> page = new PageImpl<>(
                List.of(fixtureWithStatus(owner, ListingStatus.REMOVED)), pageable, 1L);
        when(listings.findByOwner(owner, pageable)).thenReturn(page);

        svc.listMine(owner, pageable, null, true);

        verify(listings).findByOwner(owner, pageable);
        verify(listings, never()).findByOwnerAndStatus(any(), any(), any());
        verify(listings, never()).findByOwnerAndStatusNot(any(), any(), any());
    }

    // ---------- getOne ----------

    @Test
    void getOneUnknownListingThrowsListingNotFound() {
        when(listings.findById(LISTING_ID)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.getOne(owner, LISTING_ID));

        assertEquals(ErrorCode.LISTING_NOT_FOUND, ex.getCode());
    }

    @Test
    void getOneOwnerSeesOwnRemovedListing() {
        // spec §4.3: owner sees their listing in any status, including REMOVED.
        when(listings.findById(LISTING_ID))
                .thenReturn(Optional.of(fixtureWithStatus(owner, ListingStatus.REMOVED)));

        ListingResponse r = svc.getOne(owner, LISTING_ID);

        assertNotNull(r);
        assertEquals(ListingStatus.REMOVED, r.status());
    }

    @Test
    void getOneNonOwnerCannotSeeRemovedListing() {
        // spec §4.3: a REMOVED listing yields LISTING_NOT_FOUND for non-owners (anti-enumeration).
        when(listings.findById(LISTING_ID))
                .thenReturn(Optional.of(fixtureWithStatus(owner, ListingStatus.REMOVED)));

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.getOne(otherUser, LISTING_ID));

        assertEquals(ErrorCode.LISTING_NOT_FOUND, ex.getCode());
    }

    // ---------- remove ----------

    @Test
    void removeAvailableListingMarksItRemovedAndSaves() {
        Listing existing = fixtureWithStatus(owner, ListingStatus.AVAILABLE);
        when(listings.findById(LISTING_ID)).thenReturn(Optional.of(existing));
        when(listings.save(any(Listing.class))).thenAnswer(inv -> inv.getArgument(0));

        svc.remove(owner, LISTING_ID);

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listings).save(captor.capture());
        assertEquals(ListingStatus.REMOVED, captor.getValue().getStatus());
    }

    @Test
    void removeAlreadyRemovedListingIsIdempotentNoop() {
        // spec §4.6: DELETE is "equivalent to PATCH /status with REMOVED" and its
        // errors list does not include INVALID_STATUS_TRANSITION → DELETE is
        // idempotent: removing an already-REMOVED listing succeeds silently.
        Listing existing = fixtureWithStatus(owner, ListingStatus.REMOVED);
        when(listings.findById(LISTING_ID)).thenReturn(Optional.of(existing));

        svc.remove(owner, LISTING_ID);

        verify(listings, never()).save(any());
    }

    @Test
    void removeNonOwnerThrowsListingNotFound() {
        // Bonus coverage: non-owner remove path returns the same anti-enumeration code as
        // update / changeStatus.
        when(listings.findById(LISTING_ID))
                .thenReturn(Optional.of(fixtureWithStatus(owner, ListingStatus.AVAILABLE)));

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.remove(otherUser, LISTING_ID));

        assertEquals(ErrorCode.LISTING_NOT_FOUND, ex.getCode());
        verify(listings, never()).save(any());
    }
}
