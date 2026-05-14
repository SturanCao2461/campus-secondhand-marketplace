package nz.ac.waikato.campusmarketplace.repository;

import nz.ac.waikato.campusmarketplace.AbstractIntegrationTest;
import nz.ac.waikato.campusmarketplace.entity.Category;
import nz.ac.waikato.campusmarketplace.entity.Condition;
import nz.ac.waikato.campusmarketplace.entity.Listing;
import nz.ac.waikato.campusmarketplace.entity.ListingStatus;
import nz.ac.waikato.campusmarketplace.entity.ListingType;
import nz.ac.waikato.campusmarketplace.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ListingRepositoryTest extends AbstractIntegrationTest {

    private static final String BCRYPT_DUMMY = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired ListingRepository listings;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;

    private User owner;
    private User stranger;
    private Listing ownerAvailable;
    private Listing ownerReserved;
    private Listing ownerSold;
    private Listing ownerRemoved;
    private Listing strangerAvailable;

    @BeforeEach
    void setupFixture() {
        listings.deleteAll();
        users.deleteAll();

        owner = users.save(User.builder()
                .email("owner@students.waikato.ac.nz")
                .password(BCRYPT_DUMMY)
                .nickname("Owner")
                .build());
        stranger = users.save(User.builder()
                .email("stranger@students.waikato.ac.nz")
                .password(BCRYPT_DUMMY)
                .nickname("Stranger")
                .build());

        Category books = categories.findByCode("BOOKS").orElseThrow();

        ownerAvailable = saveListing(owner, books, "owner-avail.jpg",   ListingStatus.AVAILABLE);
        ownerReserved  = saveListing(owner, books, "owner-resv.jpg",    ListingStatus.RESERVED);
        ownerSold      = saveListing(owner, books, "owner-sold.jpg",    ListingStatus.SOLD);
        ownerRemoved   = saveListing(owner, books, "owner-removed.jpg", ListingStatus.REMOVED);
        strangerAvailable = saveListing(stranger, books, "stranger-avail.jpg", ListingStatus.AVAILABLE);
    }

    private Listing saveListing(User u, Category c, String filename, ListingStatus s) {
        return listings.save(Listing.builder()
                .owner(u)
                .category(c)
                .title("Item " + filename)
                .description("Test fixture listing")
                .price(new BigDecimal("10.00"))
                .imagePath("listings/" + filename)
                .status(s)
                .listingType(ListingType.SELL)
                .condition(Condition.GOOD)
                .build());
    }

    @Test
    void findByOwnerOnlyReturnsOwnerListings() {
        Page<Listing> page = listings.findByOwner(owner, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).extracting(l -> l.getOwner().getId())
                .containsOnly(owner.getId());
    }

    @Test
    void findByOwnerHonorsPageableSize() {
        Page<Listing> page = listings.findByOwner(owner, PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void findByOwnerHonorsSortDescendingById() {
        Pageable byIdDesc = PageRequest.of(0, 10, Sort.by("id").descending());
        Page<Listing> page = listings.findByOwner(owner, byIdDesc);

        assertThat(page.getContent()).extracting(Listing::getId)
                .containsExactly(
                        ownerRemoved.getId(),
                        ownerSold.getId(),
                        ownerReserved.getId(),
                        ownerAvailable.getId());
    }

    @Test
    void findByOwnerAndStatusFiltersCorrectly() {
        Page<Listing> page = listings.findByOwnerAndStatus(
                owner, ListingStatus.AVAILABLE, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(ownerAvailable.getId());
    }

    @Test
    void findByOwnerAndStatusNotExcludesGivenStatus() {
        Page<Listing> page = listings.findByOwnerAndStatusNot(
                owner, ListingStatus.REMOVED, PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(Listing::getStatus)
                .doesNotContain(ListingStatus.REMOVED)
                .containsExactlyInAnyOrder(
                        ListingStatus.AVAILABLE,
                        ListingStatus.RESERVED,
                        ListingStatus.SOLD);
    }

    @Test
    void findByImagePathReturnsListingWhenExists() {
        Optional<Listing> found = listings.findByImagePath("listings/owner-avail.jpg");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(ownerAvailable.getId());
        assertThat(found.get().getOwner().getId()).isEqualTo(owner.getId());
    }

    @Test
    void findByImagePathReturnsEmptyWhenNotFound() {
        Optional<Listing> found = listings.findByImagePath("listings/does-not-exist.jpg");

        assertThat(found).isEmpty();
    }
}
