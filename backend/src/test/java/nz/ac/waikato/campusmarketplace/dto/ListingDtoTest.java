package nz.ac.waikato.campusmarketplace.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import nz.ac.waikato.campusmarketplace.entity.Category;
import nz.ac.waikato.campusmarketplace.entity.Condition;
import nz.ac.waikato.campusmarketplace.entity.Listing;
import nz.ac.waikato.campusmarketplace.entity.ListingStatus;
import nz.ac.waikato.campusmarketplace.entity.ListingType;
import nz.ac.waikato.campusmarketplace.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ListingDtoTest {

    private static final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private Listing buildSampleListing() {
        User owner = User.builder().id(7L).email("o@x").nickname("Owner").build();
        Category cat = Category.builder()
                .id(1L).code("BOOKS").nameEn("Books & Textbooks").nameZh("书籍教材")
                .sortOrder(10).active(true).build();
        return Listing.builder()
                .id(42L)
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
                .createdAt(LocalDateTime.parse("2026-05-14T10:30:00"))
                .updatedAt(LocalDateTime.parse("2026-05-14T11:00:00"))
                .build();
    }

    @Test
    void listingResponseFromEntityBuildsImageUrlWithApiUploadsPrefix() {
        ListingResponse r = ListingResponse.from(buildSampleListing());

        assertThat(r.imageUrl()).isEqualTo("/api/uploads/listings/abc-123.jpg");
    }

    @Test
    void listingResponseFromEntityCarriesNestedCategoryResponse() {
        ListingResponse r = ListingResponse.from(buildSampleListing());

        assertThat(r.category()).isEqualTo(
                new CategoryResponse("BOOKS", "Books & Textbooks", "书籍教材"));
        assertThat(r.id()).isEqualTo(42L);
        assertThat(r.ownerId()).isEqualTo(7L);
        assertThat(r.title()).isEqualTo("Calculus 7e");
        assertThat(r.description()).isEqualTo("Used textbook in great condition");
        assertThat(r.price()).isEqualByComparingTo(new BigDecimal("45.00"));
        assertThat(r.condition()).isEqualTo(Condition.GOOD);
        assertThat(r.negotiable()).isTrue();
    }

    @Test
    void listingSummaryOmitsLongFields() {
        ListingSummary s = ListingSummary.from(buildSampleListing());

        assertThat(s.id()).isEqualTo(42L);
        assertThat(s.title()).isEqualTo("Calculus 7e");
        assertThat(s.imageUrl()).isEqualTo("/api/uploads/listings/abc-123.jpg");
        assertThat(s.status()).isEqualTo(ListingStatus.AVAILABLE);
        assertThat(s.listingType()).isEqualTo(ListingType.SELL);
        assertThat(s.category().code()).isEqualTo("BOOKS");
        assertThat(s.createdAt()).isEqualTo(LocalDateTime.parse("2026-05-14T10:30:00"));
        // Slim DTO has no description / meetAt / reasonForSelling fields at all.
        // Compile-time check: ListingSummary record components don't include them.
    }

    @Test
    void pagedListingsFromSpringPageMapsMetadata() {
        Listing one = buildSampleListing();
        Page<Listing> springPage = new PageImpl<>(
                List.of(one), PageRequest.of(2, 12), 50L);

        PagedListings p = PagedListings.from(springPage);

        assertThat(p.page()).isEqualTo(2);
        assertThat(p.pageSize()).isEqualTo(12);
        assertThat(p.totalItems()).isEqualTo(50L);
        assertThat(p.totalPages()).isEqualTo(5); // 50 / 12 → 5 pages
    }

    @Test
    void pagedListingsItemsAreSummariesNotFullEntities() {
        Listing one = buildSampleListing();
        Page<Listing> springPage = new PageImpl<>(List.of(one), PageRequest.of(0, 10), 1L);

        PagedListings p = PagedListings.from(springPage);

        assertThat(p.items()).hasSize(1);
        assertThat(p.items().get(0)).isInstanceOf(ListingSummary.class);
        assertThat(p.items().get(0).id()).isEqualTo(42L);
    }

    @Test
    void createListingRequestRejectsTitleOver80Chars() {
        String tooLong = "x".repeat(81);
        CreateListingRequest req = new CreateListingRequest(
                tooLong, "ok desc", "BOOKS", ListingType.SELL,
                new BigDecimal("10.00"), null, null, null, null, null);

        Set<ConstraintViolation<CreateListingRequest>> v = validator.validate(req);

        assertThat(v).extracting(ConstraintViolation::getPropertyPath)
                .anySatisfy(path -> assertThat(path.toString()).isEqualTo("title"));
    }

    @Test
    void createListingRequestRejectsBlankTitle() {
        CreateListingRequest req = new CreateListingRequest(
                "  ", "ok desc", "BOOKS", ListingType.SELL,
                new BigDecimal("10.00"), null, null, null, null, null);

        Set<ConstraintViolation<CreateListingRequest>> v = validator.validate(req);

        assertThat(v).extracting(ConstraintViolation::getPropertyPath)
                .anySatisfy(path -> assertThat(path.toString()).isEqualTo("title"));
    }
}
