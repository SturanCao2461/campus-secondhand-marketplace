package nz.ac.waikato.campusmarketplace.entity;

import nz.ac.waikato.campusmarketplace.AbstractIntegrationTest;
import nz.ac.waikato.campusmarketplace.repository.CategoryRepository;
import nz.ac.waikato.campusmarketplace.repository.ConversationRepository;
import nz.ac.waikato.campusmarketplace.repository.ListingRepository;
import nz.ac.waikato.campusmarketplace.repository.MessageRepository;
import nz.ac.waikato.campusmarketplace.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
class ListingSchemaIntegrationTest extends AbstractIntegrationTest {

    private static final String BCRYPT_DUMMY = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Autowired ListingRepository listings;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ConversationRepository conversationRepo;
    @Autowired MessageRepository messageRepo;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        messageRepo.deleteAllInBatch();
        conversationRepo.deleteAllInBatch();
        listings.deleteAllInBatch();
        users.deleteAllInBatch();
    }

    @Test
    void listingsTableExists() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() AND table_name = 'listings'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void fourExpectedNamedIndexesExist() {
        List<String> indexNames = jdbc.queryForList(
                "SELECT DISTINCT index_name FROM information_schema.statistics " +
                        "WHERE table_schema = DATABASE() AND table_name = 'listings'",
                String.class);
        assertThat(indexNames).contains(
                "idx_listings_owner",
                "idx_listings_status",
                "idx_listings_created",
                "idx_listings_image"
        );
    }

    @Test
    void saveAndLoadListingPreservesAllFields() {
        User owner = users.save(User.builder()
                .email("seller@students.waikato.ac.nz")
                .password(BCRYPT_DUMMY)
                .nickname("Seller")
                .build());
        Category cat = categories.findByCode("BOOKS").orElseThrow();

        Listing draft = Listing.builder()
                .owner(owner)
                .category(cat)
                .title("Calculus 7e")
                .description("Used textbook in great condition")
                .price(new BigDecimal("45.00"))
                .originalPrice(new BigDecimal("129.00"))
                .imagePath("listings/abc-123.jpg")
                .status(ListingStatus.AVAILABLE)
                .listingType(ListingType.SELL)
                .condition(Condition.GOOD)
                .meetAt("Library cafe")
                .negotiable(true)
                .reasonForSelling("Already finished the course")
                .build();
        Listing saved = listings.save(draft);

        Listing loaded = listings.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(loaded.getCategory().getCode()).isEqualTo("BOOKS");
        assertThat(loaded.getTitle()).isEqualTo("Calculus 7e");
        assertThat(loaded.getDescription()).isEqualTo("Used textbook in great condition");
        assertThat(loaded.getPrice()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(loaded.getOriginalPrice()).isEqualByComparingTo(new BigDecimal("129.00"));
        assertThat(loaded.getImagePath()).isEqualTo("listings/abc-123.jpg");
        assertThat(loaded.getStatus()).isEqualTo(ListingStatus.AVAILABLE);
        assertThat(loaded.getListingType()).isEqualTo(ListingType.SELL);
        assertThat(loaded.getCondition()).isEqualTo(Condition.GOOD);
        assertThat(loaded.getMeetAt()).isEqualTo("Library cafe");
        assertThat(loaded.getNegotiable()).isTrue();
        assertThat(loaded.getReasonForSelling()).isEqualTo("Already finished the course");
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getDeletedAt()).isNull();
    }

    @Test
    void enumsPersistAsStringNotOrdinal() {
        User owner = users.save(User.builder()
                .email("enum-check@students.waikato.ac.nz")
                .password(BCRYPT_DUMMY)
                .nickname("EnumCheck")
                .build());
        Category cat = categories.findByCode("BOOKS").orElseThrow();

        Listing l = listings.save(Listing.builder()
                .owner(owner)
                .category(cat)
                .title("Spot-check")
                .description("Verifying enum string persistence")
                .imagePath("listings/spot-check.jpg")
                .status(ListingStatus.RESERVED)
                .listingType(ListingType.GIVEAWAY)
                .condition(Condition.LIKE_NEW)
                .build());

        String status = jdbc.queryForObject(
                "SELECT status FROM listings WHERE id = ?", String.class, l.getId());
        String listingType = jdbc.queryForObject(
                "SELECT listing_type FROM listings WHERE id = ?", String.class, l.getId());
        String conditionVal = jdbc.queryForObject(
                "SELECT `condition` FROM listings WHERE id = ?", String.class, l.getId());

        assertThat(status).isEqualTo("RESERVED");
        assertThat(listingType).isEqualTo("GIVEAWAY");
        assertThat(conditionVal).isEqualTo("LIKE_NEW");
    }
}
