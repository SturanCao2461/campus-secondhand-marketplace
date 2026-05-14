package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.dto.CreateListingRequest;
import nz.ac.waikato.campusmarketplace.dto.ListingResponse;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListingServiceCreateTest {

    private static final String IMAGE_PATH = "listings/placeholder.jpg";

    CategoryRepository categories;
    ListingRepository listings;
    ListingService svc;

    User owner;
    Category booksActive;
    Category booksInactive;

    @BeforeEach
    void setUp() {
        categories = mock(CategoryRepository.class);
        listings = mock(ListingRepository.class);
        svc = new ListingService(categories, listings);

        owner = User.builder().id(7L).email("o@x").nickname("Owner").build();
        booksActive = Category.builder()
                .id(1L).code("BOOKS").nameEn("Books").nameZh("书籍")
                .sortOrder(10).active(true).build();
        booksInactive = Category.builder()
                .id(99L).code("DISCONTINUED").nameEn("X").nameZh("X")
                .sortOrder(99).active(false).build();

        when(listings.save(any(Listing.class))).thenAnswer(inv -> {
            Listing l = inv.getArgument(0);
            l.setId(42L);
            return l;
        });
    }

    private CreateListingRequest sellRequest(BigDecimal price, BigDecimal originalPrice) {
        return new CreateListingRequest(
                "Calculus 7e", "Used textbook", "BOOKS", ListingType.SELL,
                price, originalPrice, Condition.GOOD, "Library cafe", true, "Finished course");
    }

    private CreateListingRequest giveawayRequest(BigDecimal price) {
        return new CreateListingRequest(
                "Free notebook", "Brand new", "BOOKS", ListingType.GIVEAWAY,
                price, null, null, null, null, null);
    }

    @Test
    void sellHappyPathReturnsResponseWithSavedFields() {
        when(categories.findByCode("BOOKS")).thenReturn(Optional.of(booksActive));

        ListingResponse r = svc.create(owner, sellRequest(new BigDecimal("45.00"), new BigDecimal("129.00")), IMAGE_PATH);

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listings).save(captor.capture());
        Listing saved = captor.getValue();
        assertEquals(owner, saved.getOwner());
        assertEquals(booksActive, saved.getCategory());
        assertEquals("Calculus 7e", saved.getTitle());
        assertEquals(0, new BigDecimal("45.00").compareTo(saved.getPrice()));
        assertEquals(0, new BigDecimal("129.00").compareTo(saved.getOriginalPrice()));
        assertEquals(ListingType.SELL, saved.getListingType());
        assertEquals(ListingStatus.AVAILABLE, saved.getStatus());
        assertEquals(IMAGE_PATH, saved.getImagePath());

        assertNotNull(r);
        assertEquals(42L, r.id());
        assertEquals("/api/uploads/" + IMAGE_PATH, r.imageUrl());
    }

    @Test
    void giveawayDiscardsRequestPriceAndPersistsNull() {
        when(categories.findByCode("BOOKS")).thenReturn(Optional.of(booksActive));

        svc.create(owner, giveawayRequest(new BigDecimal("99.00")), IMAGE_PATH);

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listings).save(captor.capture());
        Listing saved = captor.getValue();
        assertEquals(ListingType.GIVEAWAY, saved.getListingType());
        assertNull(saved.getPrice());
    }

    @Test
    void unknownCategoryCodeThrowsInvalidCategory() {
        when(categories.findByCode("BOOKS")).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.create(owner, sellRequest(new BigDecimal("10.00"), null), IMAGE_PATH));

        assertEquals(ErrorCode.INVALID_CATEGORY, ex.getCode());
        verify(listings, never()).save(any());
    }

    @Test
    void inactiveCategoryThrowsInvalidCategory() {
        when(categories.findByCode("BOOKS")).thenReturn(Optional.of(booksInactive));

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.create(owner, sellRequest(new BigDecimal("10.00"), null), IMAGE_PATH));

        assertEquals(ErrorCode.INVALID_CATEGORY, ex.getCode());
        verify(listings, never()).save(any());
    }

    @Test
    void sellWithNullPriceThrowsInvalidPrice() {
        when(categories.findByCode("BOOKS")).thenReturn(Optional.of(booksActive));

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.create(owner, sellRequest(null, null), IMAGE_PATH));

        assertEquals(ErrorCode.INVALID_PRICE, ex.getCode());
        verify(listings, never()).save(any());
    }

    @Test
    void sellWithZeroPriceThrowsInvalidPrice() {
        when(categories.findByCode("BOOKS")).thenReturn(Optional.of(booksActive));

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.create(owner, sellRequest(BigDecimal.ZERO, null), IMAGE_PATH));

        assertEquals(ErrorCode.INVALID_PRICE, ex.getCode());
        verify(listings, never()).save(any());
    }

    @Test
    void nonPositiveOriginalPriceThrowsInvalidPrice() {
        when(categories.findByCode("BOOKS")).thenReturn(Optional.of(booksActive));

        ApiException ex = assertThrows(ApiException.class,
                () -> svc.create(owner,
                        sellRequest(new BigDecimal("10.00"), new BigDecimal("-1.00")),
                        IMAGE_PATH));

        assertEquals(ErrorCode.INVALID_PRICE, ex.getCode());
        verify(listings, never()).save(any());
    }

    @Test
    void nullNegotiableDefaultsToFalse() {
        when(categories.findByCode("BOOKS")).thenReturn(Optional.of(booksActive));

        CreateListingRequest req = new CreateListingRequest(
                "Title", "Description", "BOOKS", ListingType.SELL,
                new BigDecimal("10.00"), null, null, null, null, null);

        svc.create(owner, req, IMAGE_PATH);

        ArgumentCaptor<Listing> captor = ArgumentCaptor.forClass(Listing.class);
        verify(listings).save(captor.capture());
        assertFalse(captor.getValue().getNegotiable());
    }
}
