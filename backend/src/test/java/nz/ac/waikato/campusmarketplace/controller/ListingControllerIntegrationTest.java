package nz.ac.waikato.campusmarketplace.controller;

import com.fasterxml.jackson.databind.JsonNode;
import nz.ac.waikato.campusmarketplace.AbstractIntegrationTest;
import nz.ac.waikato.campusmarketplace.repository.ListingRepository;
import nz.ac.waikato.campusmarketplace.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ListingControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository users;
    @Autowired ListingRepository listings;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        listings.deleteAll();
        users.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void createAndListMineRoundTrip() {
        register("seller@students.waikato.ac.nz", "Pass1234", "Seller");
        String cookie = loginAndGetCookie("seller@students.waikato.ac.nz", "Pass1234");

        ResponseEntity<JsonNode> created = postJson("/api/listings", cookie, Map.of(
                "title", "Calculus 7e",
                "description", "Used textbook in great condition",
                "categoryCode", "BOOKS",
                "listingType", "SELL",
                "price", new BigDecimal("45.00"),
                "originalPrice", new BigDecimal("129.00"),
                "condition", "GOOD",
                "meetAt", "Library cafe",
                "negotiable", true,
                "reasonForSelling", "Finished the course"
        ));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long createdId = created.getBody().get("id").asLong();
        assertThat(created.getBody().get("imageUrl").asText())
                .isEqualTo("/api/uploads/listings/placeholder.jpg");
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
    void nonOwnerGetOneReturnsNotFound() {
        register("owner@students.waikato.ac.nz", "Pass1234", "Owner");
        String ownerCookie = loginAndGetCookie("owner@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> created = postJson("/api/listings", ownerCookie, Map.of(
                "title", "Owner Item", "description", "Test",
                "categoryCode", "BOOKS", "listingType", "SELL",
                "price", new BigDecimal("10.00")));
        long listingId = created.getBody().get("id").asLong();

        register("stranger@students.waikato.ac.nz", "Pass5678", "Stranger");
        String strangerCookie = loginAndGetCookie("stranger@students.waikato.ac.nz", "Pass5678");
        ResponseEntity<JsonNode> r = getJson("/api/listings/" + listingId, strangerCookie);

        // Stranger sees the same 404 LISTING_NOT_FOUND for AVAILABLE-but-not-mine
        // is permitted (Epic 3 will broaden), but spec §4.3 only requires non-owners
        // to be hidden from REMOVED. Mark stranger's listing as REMOVED to exercise
        // the anti-enumeration path.
        ResponseEntity<JsonNode> markRemoved = patchJson(
                "/api/listings/" + listingId + "/status", ownerCookie,
                Map.of("newStatus", "REMOVED"));
        assertThat(markRemoved.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> r2 = getJson("/api/listings/" + listingId, strangerCookie);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r2.getBody().get("code").asText()).isEqualTo("LISTING_NOT_FOUND");
    }

    @Test
    void deleteIsIdempotent() {
        register("deleter@students.waikato.ac.nz", "Pass1234", "Deleter");
        String cookie = loginAndGetCookie("deleter@students.waikato.ac.nz", "Pass1234");
        ResponseEntity<JsonNode> created = postJson("/api/listings", cookie, Map.of(
                "title", "To Delete", "description", "Test",
                "categoryCode", "BOOKS", "listingType", "SELL",
                "price", new BigDecimal("10.00")));
        long listingId = created.getBody().get("id").asLong();

        ResponseEntity<Void> first = deleteJson("/api/listings/" + listingId, cookie);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // spec §4.6: DELETE on already-REMOVED is a silent success (no INVALID_STATUS_TRANSITION).
        ResponseEntity<Void> second = deleteJson("/api/listings/" + listingId, cookie);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ---------- helpers ----------

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

    private ResponseEntity<JsonNode> postJson(String url, String cookie, Map<String, ?> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class);
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

    private ResponseEntity<Void> deleteJson(String url, String cookie) {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange(url, HttpMethod.DELETE, new HttpEntity<>(h), Void.class);
    }
}
