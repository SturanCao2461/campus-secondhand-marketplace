package nz.ac.waikato.campusmarketplace.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nz.ac.waikato.campusmarketplace.AbstractIntegrationTest;
import nz.ac.waikato.campusmarketplace.repository.ListingRepository;
import nz.ac.waikato.campusmarketplace.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ListingControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository users;
    @Autowired ListingRepository listings;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper json;

    @BeforeEach
    void clean() {
        listings.deleteAllInBatch();
        users.deleteAllInBatch();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void createAndListMineRoundTrip() throws Exception {
        register("seller@students.waikato.ac.nz", "Pass1234", "Seller");
        String cookie = loginAndGetCookie("seller@students.waikato.ac.nz", "Pass1234");

        ResponseEntity<JsonNode> created = createListing(cookie,
                """
                {"title":"Calculus 7e","description":"Used textbook in great condition",
                 "categoryCode":"BOOKS","listingType":"SELL","price":45.00,
                 "originalPrice":129.00,"condition":"GOOD","meetAt":"Library cafe",
                 "negotiable":true,"reasonForSelling":"Finished the course"}
                """);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long createdId = created.getBody().get("id").asLong();
        assertThat(created.getBody().get("imageUrl").asText()).startsWith("/api/uploads/listings/");
        assertThat(created.getBody().get("imageUrl").asText()).endsWith(".jpg");
        assertThat(created.getBody().get("status").asText()).isEqualTo("AVAILABLE");

        ResponseEntity<JsonNode> mine = getJson("/api/listings/me", cookie);
        assertThat(mine.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode items = mine.getBody().get("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).get("id").asLong()).isEqualTo(createdId);
        assertThat(items.get(0).get("title").asText()).isEqualTo("Calculus 7e");
    }

    @Test
    void nonOwnerGetOneReturnsNotFoundForRemovedListing() throws Exception {
        register("owner@students.waikato.ac.nz", "Pass1234", "Owner");
        String ownerCookie = loginAndGetCookie("owner@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> created = createListing(ownerCookie,
                """
                {"title":"Owner Item","description":"Test",
                 "categoryCode":"BOOKS","listingType":"SELL","price":10.00}
                """);
        long listingId = created.getBody().get("id").asLong();

        // Mark as REMOVED
        patchJson("/api/listings/" + listingId + "/status", ownerCookie,
                Map.of("newStatus", "REMOVED"));

        register("stranger@students.waikato.ac.nz", "Pass5678", "Stranger");
        String strangerCookie = loginAndGetCookie("stranger@students.waikato.ac.nz", "Pass5678");
        ResponseEntity<JsonNode> r = getJson("/api/listings/" + listingId, strangerCookie);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody().get("code").asText()).isEqualTo("LISTING_NOT_FOUND");
    }

    @Test
    void deleteIsIdempotent() throws Exception {
        register("deleter@students.waikato.ac.nz", "Pass1234", "Deleter");
        String cookie = loginAndGetCookie("deleter@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> created = createListing(cookie,
                """
                {"title":"To Delete","description":"Test",
                 "categoryCode":"BOOKS","listingType":"SELL","price":10.00}
                """);
        long listingId = created.getBody().get("id").asLong();

        ResponseEntity<Void> first = deleteReq("/api/listings/" + listingId, cookie);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Void> second = deleteReq("/api/listings/" + listingId, cookie);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ---------- T22: create / list / get / update ----------

    @Test
    void createWithInvalidCategoryReturns400() throws Exception {
        register("cat-err@students.waikato.ac.nz", "Pass1234", "CatErr");
        String cookie = loginAndGetCookie("cat-err@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> r = createListing(cookie,
                """
                {"title":"Bad Cat","description":"Test",
                 "categoryCode":"NONEXISTENT","listingType":"SELL","price":10.00}
                """);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().get("code").asText()).isEqualTo("INVALID_CATEGORY");
    }

    @Test
    void createSellWithoutPriceReturns400() throws Exception {
        register("no-price@students.waikato.ac.nz", "Pass1234", "NoPrice");
        String cookie = loginAndGetCookie("no-price@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> r = createListing(cookie,
                """
                {"title":"No Price","description":"Test",
                 "categoryCode":"BOOKS","listingType":"SELL"}
                """);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().get("code").asText()).isEqualTo("INVALID_PRICE");
    }

    @Test
    void createGiveawayIgnoresPrice() throws Exception {
        register("giveaway@students.waikato.ac.nz", "Pass1234", "Giver");
        String cookie = loginAndGetCookie("giveaway@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> r = createListing(cookie,
                """
                {"title":"Free Item","description":"Test",
                 "categoryCode":"BOOKS","listingType":"GIVEAWAY","price":99.00}
                """);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody().get("price").isNull()).isTrue();
        assertThat(r.getBody().get("listingType").asText()).isEqualTo("GIVEAWAY");
    }

    @Test
    void unauthenticatedCreateReturns401() throws Exception {
        ResponseEntity<JsonNode> r = createListing("invalid-cookie=xyz",
                """
                {"title":"Unauth","description":"Test",
                 "categoryCode":"BOOKS","listingType":"SELL","price":10.00}
                """);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getOneReturnsFullListingResponse() throws Exception {
        register("detail@students.waikato.ac.nz", "Pass1234", "Detail");
        String cookie = loginAndGetCookie("detail@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> created = createListing(cookie,
                """
                {"title":"Detail Test","description":"Full description here",
                 "categoryCode":"ELECTRONICS","listingType":"SELL","price":99.99,
                 "condition":"LIKE_NEW","meetAt":"Hub","negotiable":true}
                """);
        long id = created.getBody().get("id").asLong();

        ResponseEntity<JsonNode> r = getJson("/api/listings/" + id, cookie);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("title").asText()).isEqualTo("Detail Test");
        assertThat(r.getBody().get("description").asText()).isEqualTo("Full description here");
        assertThat(r.getBody().get("condition").asText()).isEqualTo("LIKE_NEW");
        assertThat(r.getBody().get("meetAt").asText()).isEqualTo("Hub");
        assertThat(r.getBody().get("negotiable").asBoolean()).isTrue();
        assertThat(r.getBody().get("category").get("code").asText()).isEqualTo("ELECTRONICS");
    }

    @Test
    void updateChangesFieldsAndKeepsImage() throws Exception {
        register("updater@students.waikato.ac.nz", "Pass1234", "Updater");
        String cookie = loginAndGetCookie("updater@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> created = createListing(cookie,
                """
                {"title":"Original","description":"Orig desc",
                 "categoryCode":"BOOKS","listingType":"SELL","price":20.00}
                """);
        long id = created.getBody().get("id").asLong();
        String originalImageUrl = created.getBody().get("imageUrl").asText();

        ResponseEntity<JsonNode> updated = updateListing(cookie, id,
                """
                {"title":"Updated","description":"New desc",
                 "categoryCode":"ELECTRONICS","listingType":"SELL","price":50.00}
                """, false);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("title").asText()).isEqualTo("Updated");
        assertThat(updated.getBody().get("description").asText()).isEqualTo("New desc");
        assertThat(updated.getBody().get("price").asDouble()).isEqualTo(50.00);
        assertThat(updated.getBody().get("category").get("code").asText()).isEqualTo("ELECTRONICS");
        assertThat(updated.getBody().get("imageUrl").asText()).isEqualTo(originalImageUrl);
    }

    @Test
    void updateRemovedListingReturns400() throws Exception {
        register("upd-rem@students.waikato.ac.nz", "Pass1234", "UpdRem");
        String cookie = loginAndGetCookie("upd-rem@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> created = createListing(cookie,
                """
                {"title":"Will Remove","description":"Test",
                 "categoryCode":"BOOKS","listingType":"SELL","price":10.00}
                """);
        long id = created.getBody().get("id").asLong();
        deleteReq("/api/listings/" + id, cookie);

        ResponseEntity<JsonNode> r = updateListing(cookie, id,
                """
                {"title":"Try Update","description":"Test",
                 "categoryCode":"BOOKS","listingType":"SELL","price":10.00}
                """, false);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().get("code").asText()).isEqualTo("LISTING_REMOVED");
    }

    // ---------- T23: status / delete / image / categories ----------

    @Test
    void statusTransitionAvailableToReservedToSold() throws Exception {
        register("fsm@students.waikato.ac.nz", "Pass1234", "FSM");
        String cookie = loginAndGetCookie("fsm@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> created = createListing(cookie,
                """
                {"title":"FSM Test","description":"Test",
                 "categoryCode":"BOOKS","listingType":"SELL","price":10.00}
                """);
        long id = created.getBody().get("id").asLong();

        ResponseEntity<JsonNode> r1 = patchJson("/api/listings/" + id + "/status", cookie,
                Map.of("newStatus", "RESERVED"));
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r1.getBody().get("status").asText()).isEqualTo("RESERVED");

        ResponseEntity<JsonNode> r2 = patchJson("/api/listings/" + id + "/status", cookie,
                Map.of("newStatus", "SOLD"));
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r2.getBody().get("status").asText()).isEqualTo("SOLD");
    }

    @Test
    void statusTransitionSoldBackToAvailable() throws Exception {
        register("relist@students.waikato.ac.nz", "Pass1234", "Relist");
        String cookie = loginAndGetCookie("relist@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> created = createListing(cookie,
                """
                {"title":"Relist Test","description":"Test",
                 "categoryCode":"BOOKS","listingType":"SELL","price":10.00}
                """);
        long id = created.getBody().get("id").asLong();
        patchJson("/api/listings/" + id + "/status", cookie, Map.of("newStatus", "SOLD"));

        ResponseEntity<JsonNode> r = patchJson("/api/listings/" + id + "/status", cookie,
                Map.of("newStatus", "AVAILABLE"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("status").asText()).isEqualTo("AVAILABLE");
    }

    @Test
    void invalidStatusTransitionReturns400() throws Exception {
        register("bad-fsm@students.waikato.ac.nz", "Pass1234", "BadFSM");
        String cookie = loginAndGetCookie("bad-fsm@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> created = createListing(cookie,
                """
                {"title":"Bad FSM","description":"Test",
                 "categoryCode":"BOOKS","listingType":"SELL","price":10.00}
                """);
        long id = created.getBody().get("id").asLong();
        deleteReq("/api/listings/" + id, cookie);

        ResponseEntity<JsonNode> r = patchJson("/api/listings/" + id + "/status", cookie,
                Map.of("newStatus", "AVAILABLE"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().get("code").asText()).isEqualTo("INVALID_STATUS_TRANSITION");
    }

    @Test
    void nonOwnerDeleteReturns404() throws Exception {
        register("own-del@students.waikato.ac.nz", "Pass1234", "OwnDel");
        String ownerCookie = loginAndGetCookie("own-del@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> created = createListing(ownerCookie,
                """
                {"title":"Owner Only","description":"Test",
                 "categoryCode":"BOOKS","listingType":"SELL","price":10.00}
                """);
        long id = created.getBody().get("id").asLong();

        register("thief@students.waikato.ac.nz", "Pass5678", "Thief");
        String thiefCookie = loginAndGetCookie("thief@students.waikato.ac.nz", "Pass5678");
        ResponseEntity<Void> r = deleteReq("/api/listings/" + id, thiefCookie);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listMineWithStatusFilter() throws Exception {
        register("filter@students.waikato.ac.nz", "Pass1234", "Filter");
        String cookie = loginAndGetCookie("filter@students.waikato.ac.nz", "Pass1234");
        createListing(cookie, """
                {"title":"A1","description":"T","categoryCode":"BOOKS","listingType":"SELL","price":1.00}""");
        ResponseEntity<JsonNode> c2 = createListing(cookie, """
                {"title":"A2","description":"T","categoryCode":"BOOKS","listingType":"SELL","price":2.00}""");
        long id2 = c2.getBody().get("id").asLong();
        patchJson("/api/listings/" + id2 + "/status", cookie, Map.of("newStatus", "RESERVED"));

        ResponseEntity<JsonNode> all = getJson("/api/listings/me", cookie);
        assertThat(all.getBody().get("totalItems").asInt()).isEqualTo(2);

        ResponseEntity<JsonNode> reserved = getJson("/api/listings/me?status=RESERVED", cookie);
        assertThat(reserved.getBody().get("totalItems").asInt()).isEqualTo(1);
        assertThat(reserved.getBody().get("items").get(0).get("title").asText()).isEqualTo("A2");
    }

    @Test
    void imageServedToOwnerWithCacheHeaders() throws Exception {
        register("img-own@students.waikato.ac.nz", "Pass1234", "ImgOwn");
        String cookie = loginAndGetCookie("img-own@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> created = createListing(cookie,
                """
                {"title":"Img Test","description":"Test",
                 "categoryCode":"BOOKS","listingType":"SELL","price":10.00}
                """);
        String imageUrl = created.getBody().get("imageUrl").asText();

        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, cookie);
        ResponseEntity<byte[]> r = rest.exchange(imageUrl, HttpMethod.GET,
                new HttpEntity<>(h), byte[].class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getHeaders().getFirst("Cache-Control")).contains("max-age=86400");
        assertThat(r.getBody()).isNotNull();
        assertThat(r.getBody().length).isGreaterThan(0);
    }

    @Test
    void imageNotServedToNonOwner() throws Exception {
        register("img-own2@students.waikato.ac.nz", "Pass1234", "ImgOwn2");
        String ownerCookie = loginAndGetCookie("img-own2@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> created = createListing(ownerCookie,
                """
                {"title":"Img Private","description":"Test",
                 "categoryCode":"BOOKS","listingType":"SELL","price":10.00}
                """);
        String imageUrl = created.getBody().get("imageUrl").asText();

        register("img-spy@students.waikato.ac.nz", "Pass5678", "ImgSpy");
        String spyCookie = loginAndGetCookie("img-spy@students.waikato.ac.nz", "Pass5678");
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, spyCookie);
        ResponseEntity<JsonNode> r = rest.exchange(imageUrl, HttpMethod.GET,
                new HttpEntity<>(h), JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void categoriesEndpointReturns8SeededCategories() {
        register("cat-chk@students.waikato.ac.nz", "Pass1234", "CatChk");
        String cookie = loginAndGetCookie("cat-chk@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> r = getJson("/api/categories", cookie);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("items").size()).isEqualTo(8);
    }

    // ---------- helpers ----------

    private byte[] tinyJpeg() throws IOException {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    private ResponseEntity<JsonNode> createListing(String cookie, String listingJson) throws IOException {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.add(HttpHeaders.COOKIE, cookie);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders listingHeaders = new HttpHeaders();
        listingHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("listing", new HttpEntity<>(listingJson, listingHeaders));
        ByteArrayResource imageResource = new ByteArrayResource(tinyJpeg()) {
            @Override public String getFilename() { return "test.jpg"; }
        };
        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.IMAGE_JPEG);
        body.add("image", new HttpEntity<>(imageResource, imageHeaders));

        return rest.exchange("/api/listings", HttpMethod.POST,
                new HttpEntity<>(body, h), JsonNode.class);
    }

    private ResponseEntity<JsonNode> updateListing(String cookie, long id, String listingJson, boolean includeImage) throws IOException {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.add(HttpHeaders.COOKIE, cookie);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders listingHeaders = new HttpHeaders();
        listingHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("listing", new HttpEntity<>(listingJson, listingHeaders));
        if (includeImage) {
            ByteArrayResource imageResource = new ByteArrayResource(tinyJpeg()) {
                @Override public String getFilename() { return "updated.jpg"; }
            };
            HttpHeaders imageHeaders = new HttpHeaders();
            imageHeaders.setContentType(MediaType.IMAGE_JPEG);
            body.add("image", new HttpEntity<>(imageResource, imageHeaders));
        }

        return rest.exchange("/api/listings/" + id, HttpMethod.PUT,
                new HttpEntity<>(body, h), JsonNode.class);
    }

    private void register(String email, String password, String nickname) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> r = rest.exchange("/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", password, "nickname", nickname), h),
                JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String loginAndGetCookie(String email, String password) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> r = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", password), h),
                JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        String setCookie = r.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return setCookie.split(";", 2)[0];
    }

    private ResponseEntity<JsonNode> patchJson(String url, String cookie, Map<String, ?> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange(url, HttpMethod.PATCH, new HttpEntity<>(body, h), JsonNode.class);
    }

    private ResponseEntity<JsonNode> getJson(String url, String cookie) {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class);
    }

    private ResponseEntity<Void> deleteReq(String url, String cookie) {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange(url, HttpMethod.DELETE, new HttpEntity<>(h), Void.class);
    }
}
