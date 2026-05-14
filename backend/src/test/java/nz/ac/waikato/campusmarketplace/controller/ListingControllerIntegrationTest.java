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
        // listing part as JSON
        HttpHeaders listingHeaders = new HttpHeaders();
        listingHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("listing", new HttpEntity<>(listingJson, listingHeaders));
        // image part
        ByteArrayResource imageResource = new ByteArrayResource(tinyJpeg()) {
            @Override public String getFilename() { return "test.jpg"; }
        };
        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.IMAGE_JPEG);
        body.add("image", new HttpEntity<>(imageResource, imageHeaders));

        return rest.exchange("/api/listings", HttpMethod.POST,
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
