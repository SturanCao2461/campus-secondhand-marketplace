package nz.ac.waikato.campusmarketplace.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nz.ac.waikato.campusmarketplace.AbstractIntegrationTest;
import nz.ac.waikato.campusmarketplace.repository.ConversationRepository;
import nz.ac.waikato.campusmarketplace.repository.ListingRepository;
import nz.ac.waikato.campusmarketplace.repository.MessageRepository;
import nz.ac.waikato.campusmarketplace.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository users;
    @Autowired ListingRepository listings;
    @Autowired ConversationRepository conversationRepo;
    @Autowired MessageRepository messageRepo;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper json;

    @BeforeEach
    void clean() {
        messageRepo.deleteAllInBatch();
        conversationRepo.deleteAllInBatch();
        listings.deleteAllInBatch();
        users.deleteAllInBatch();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void createConversationAndSendMessage() throws Exception {
        String sellerCookie = registerAndLogin("seller@students.waikato.ac.nz", "Pass1234", "Seller");
        long listingId = createTestListing(sellerCookie);

        String buyerCookie = registerAndLogin("buyer@students.waikato.ac.nz", "Pass1234", "Buyer");

        // Create conversation
        ResponseEntity<JsonNode> created = postJson("/api/conversations", buyerCookie,
                Map.of("listingId", listingId));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long convId = created.getBody().get("id").asLong();
        assertThat(created.getBody().get("listingTitle").asText()).isNotBlank();
        assertThat(created.getBody().get("counterpartNickname").asText()).isEqualTo("Seller");

        // Send message
        ResponseEntity<JsonNode> msg = postJson("/api/conversations/" + convId + "/messages", buyerCookie,
                Map.of("content", "Is this still available?"));
        assertThat(msg.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(msg.getBody().get("content").asText()).isEqualTo("Is this still available?");
        assertThat(msg.getBody().get("senderNickname").asText()).isEqualTo("Buyer");
    }

    @Test
    void createConversationIsIdempotent() throws Exception {
        String sellerCookie = registerAndLogin("seller2@students.waikato.ac.nz", "Pass1234", "Seller2");
        long listingId = createTestListing(sellerCookie);
        String buyerCookie = registerAndLogin("buyer2@students.waikato.ac.nz", "Pass1234", "Buyer2");

        ResponseEntity<JsonNode> first = postJson("/api/conversations", buyerCookie,
                Map.of("listingId", listingId));
        ResponseEntity<JsonNode> second = postJson("/api/conversations", buyerCookie,
                Map.of("listingId", listingId));

        assertThat(first.getBody().get("id").asLong()).isEqualTo(second.getBody().get("id").asLong());
    }

    @Test
    void sellerCannotCreateConversationOnOwnListing() throws Exception {
        String sellerCookie = registerAndLogin("seller3@students.waikato.ac.nz", "Pass1234", "Seller3");
        long listingId = createTestListing(sellerCookie);

        ResponseEntity<JsonNode> r = postJson("/api/conversations", sellerCookie,
                Map.of("listingId", listingId));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().get("code").asText()).isEqualTo("CANNOT_MESSAGE_SELF");
    }

    @Test
    void unauthenticatedReturns401() {
        ResponseEntity<JsonNode> r = postJson("/api/conversations", "invalid=xyz",
                Map.of("listingId", 1));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void nonParticipantCannotAccessConversation() throws Exception {
        String sellerCookie = registerAndLogin("seller4@students.waikato.ac.nz", "Pass1234", "Seller4");
        long listingId = createTestListing(sellerCookie);
        String buyerCookie = registerAndLogin("buyer4@students.waikato.ac.nz", "Pass1234", "Buyer4");

        ResponseEntity<JsonNode> created = postJson("/api/conversations", buyerCookie,
                Map.of("listingId", listingId));
        long convId = created.getBody().get("id").asLong();

        String strangerCookie = registerAndLogin("stranger@students.waikato.ac.nz", "Pass1234", "Stranger");
        ResponseEntity<JsonNode> r = getJson("/api/conversations/" + convId, strangerCookie);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(r.getBody().get("code").asText()).isEqualTo("CONVERSATION_FORBIDDEN");
    }

    @Test
    void nonParticipantCannotSendMessage() throws Exception {
        String sellerCookie = registerAndLogin("seller5@students.waikato.ac.nz", "Pass1234", "Seller5");
        long listingId = createTestListing(sellerCookie);
        String buyerCookie = registerAndLogin("buyer5@students.waikato.ac.nz", "Pass1234", "Buyer5");

        ResponseEntity<JsonNode> created = postJson("/api/conversations", buyerCookie,
                Map.of("listingId", listingId));
        long convId = created.getBody().get("id").asLong();

        String strangerCookie = registerAndLogin("stranger5@students.waikato.ac.nz", "Pass1234", "Stranger5");
        ResponseEntity<JsonNode> r = postJson("/api/conversations/" + convId + "/messages", strangerCookie,
                Map.of("content", "Hacked!"));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void blankMessageReturns400() throws Exception {
        String sellerCookie = registerAndLogin("seller6@students.waikato.ac.nz", "Pass1234", "Seller6");
        long listingId = createTestListing(sellerCookie);
        String buyerCookie = registerAndLogin("buyer6@students.waikato.ac.nz", "Pass1234", "Buyer6");

        ResponseEntity<JsonNode> created = postJson("/api/conversations", buyerCookie,
                Map.of("listingId", listingId));
        long convId = created.getBody().get("id").asLong();

        ResponseEntity<JsonNode> r = postJson("/api/conversations/" + convId + "/messages", buyerCookie,
                Map.of("content", ""));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tooLongMessageReturns400() throws Exception {
        String sellerCookie = registerAndLogin("seller7@students.waikato.ac.nz", "Pass1234", "Seller7");
        long listingId = createTestListing(sellerCookie);
        String buyerCookie = registerAndLogin("buyer7@students.waikato.ac.nz", "Pass1234", "Buyer7");

        ResponseEntity<JsonNode> created = postJson("/api/conversations", buyerCookie,
                Map.of("listingId", listingId));
        long convId = created.getBody().get("id").asLong();

        String longContent = "x".repeat(1001);
        ResponseEntity<JsonNode> r = postJson("/api/conversations/" + convId + "/messages", buyerCookie,
                Map.of("content", longContent));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void conversationNotFoundReturns404() throws Exception {
        String buyerCookie = registerAndLogin("buyer8@students.waikato.ac.nz", "Pass1234", "Buyer8");
        ResponseEntity<JsonNode> r = getJson("/api/conversations/99999", buyerCookie);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody().get("code").asText()).isEqualTo("CONVERSATION_NOT_FOUND");
    }

    @Test
    void listingNotFoundReturns404() throws Exception {
        String buyerCookie = registerAndLogin("buyer9@students.waikato.ac.nz", "Pass1234", "Buyer9");
        ResponseEntity<JsonNode> r = postJson("/api/conversations", buyerCookie,
                Map.of("listingId", 99999));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(r.getBody().get("code").asText()).isEqualTo("LISTING_NOT_FOUND");
    }

    @Test
    void listConversationsOrderedByUpdatedAtDesc() throws Exception {
        String sellerCookie = registerAndLogin("seller10@students.waikato.ac.nz", "Pass1234", "Seller10");
        long listing1 = createTestListing(sellerCookie);
        long listing2 = createTestListing(sellerCookie);
        String buyerCookie = registerAndLogin("buyer10@students.waikato.ac.nz", "Pass1234", "Buyer10");

        ResponseEntity<JsonNode> c1 = postJson("/api/conversations", buyerCookie, Map.of("listingId", listing1));
        ResponseEntity<JsonNode> c2 = postJson("/api/conversations", buyerCookie, Map.of("listingId", listing2));
        long convId1 = c1.getBody().get("id").asLong();

        // Send message to conv1 so it becomes most recent
        postJson("/api/conversations/" + convId1 + "/messages", buyerCookie, Map.of("content", "Latest"));

        ResponseEntity<JsonNode> list = getJsonArray("/api/conversations", buyerCookie);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode items = list.getBody();
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).get("id").asLong()).isEqualTo(convId1);
    }

    @Test
    void cursorPaginationReturnsOnlyNewerMessages() throws Exception {
        String sellerCookie = registerAndLogin("seller11@students.waikato.ac.nz", "Pass1234", "Seller11");
        long listingId = createTestListing(sellerCookie);
        String buyerCookie = registerAndLogin("buyer11@students.waikato.ac.nz", "Pass1234", "Buyer11");

        ResponseEntity<JsonNode> created = postJson("/api/conversations", buyerCookie, Map.of("listingId", listingId));
        long convId = created.getBody().get("id").asLong();

        // Send 3 messages
        postJson("/api/conversations/" + convId + "/messages", buyerCookie, Map.of("content", "Msg 1"));
        ResponseEntity<JsonNode> msg2 = postJson("/api/conversations/" + convId + "/messages", buyerCookie, Map.of("content", "Msg 2"));
        long msg2Id = msg2.getBody().get("id").asLong();
        postJson("/api/conversations/" + convId + "/messages", buyerCookie, Map.of("content", "Msg 3"));

        // Fetch after msg2 — should only get msg3
        ResponseEntity<JsonNode> r = getJsonArray("/api/conversations/" + convId + "/messages?after=" + msg2Id, buyerCookie);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().size()).isEqualTo(1);
        assertThat(r.getBody().get(0).get("content").asText()).isEqualTo("Msg 3");
    }

    @Test
    void unreadCountReflectsUnreadMessages() throws Exception {
        String sellerCookie = registerAndLogin("seller12@students.waikato.ac.nz", "Pass1234", "Seller12");
        long listingId = createTestListing(sellerCookie);
        String buyerCookie = registerAndLogin("buyer12@students.waikato.ac.nz", "Pass1234", "Buyer12");

        ResponseEntity<JsonNode> created = postJson("/api/conversations", buyerCookie, Map.of("listingId", listingId));
        long convId = created.getBody().get("id").asLong();

        // Buyer sends 2 messages
        postJson("/api/conversations/" + convId + "/messages", buyerCookie, Map.of("content", "Hi"));
        postJson("/api/conversations/" + convId + "/messages", buyerCookie, Map.of("content", "Hello?"));

        // Seller should have 2 unread
        ResponseEntity<JsonNode> unread = getJson("/api/conversations/unread-count", sellerCookie);
        assertThat(unread.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unread.getBody().get("total").asLong()).isEqualTo(2L);

        // Seller reads messages
        getJsonArray("/api/conversations/" + convId + "/messages", sellerCookie);

        // Seller unread should now be 0
        ResponseEntity<JsonNode> unread2 = getJson("/api/conversations/unread-count", sellerCookie);
        assertThat(unread2.getBody().get("total").asLong()).isEqualTo(0L);
    }

    @Test
    void sellerCanReplyToConversation() throws Exception {
        String sellerCookie = registerAndLogin("seller13@students.waikato.ac.nz", "Pass1234", "Seller13");
        long listingId = createTestListing(sellerCookie);
        String buyerCookie = registerAndLogin("buyer13@students.waikato.ac.nz", "Pass1234", "Buyer13");

        ResponseEntity<JsonNode> created = postJson("/api/conversations", buyerCookie, Map.of("listingId", listingId));
        long convId = created.getBody().get("id").asLong();

        // Seller replies
        ResponseEntity<JsonNode> reply = postJson("/api/conversations/" + convId + "/messages", sellerCookie,
                Map.of("content", "Yes, still available!"));
        assertThat(reply.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(reply.getBody().get("senderNickname").asText()).isEqualTo("Seller13");
    }

    @Test
    void sellerSeesConversationInList() throws Exception {
        String sellerCookie = registerAndLogin("seller14@students.waikato.ac.nz", "Pass1234", "Seller14");
        long listingId = createTestListing(sellerCookie);
        String buyerCookie = registerAndLogin("buyer14@students.waikato.ac.nz", "Pass1234", "Buyer14");

        postJson("/api/conversations", buyerCookie, Map.of("listingId", listingId));

        ResponseEntity<JsonNode> list = getJsonArray("/api/conversations", sellerCookie);
        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(list.getBody().size()).isEqualTo(1);
        assertThat(list.getBody().get(0).get("counterpartNickname").asText()).isEqualTo("Buyer14");
    }

    // ---------- helpers ----------

    private byte[] tinyJpeg() throws IOException {
        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }

    private long createTestListing(String cookie) throws IOException {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.add(HttpHeaders.COOKIE, cookie);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders listingHeaders = new HttpHeaders();
        listingHeaders.setContentType(MediaType.APPLICATION_JSON);
        body.add("listing", new HttpEntity<>("""
                {"title":"Test Item","description":"For messaging test",
                 "categoryCode":"BOOKS","listingType":"SELL","price":25.00}
                """, listingHeaders));
        ByteArrayResource imageResource = new ByteArrayResource(tinyJpeg()) {
            @Override public String getFilename() { return "test.jpg"; }
        };
        HttpHeaders imageHeaders = new HttpHeaders();
        imageHeaders.setContentType(MediaType.IMAGE_JPEG);
        body.add("image", new HttpEntity<>(imageResource, imageHeaders));

        ResponseEntity<JsonNode> r = rest.exchange("/api/listings", HttpMethod.POST,
                new HttpEntity<>(body, h), JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return r.getBody().get("id").asLong();
    }

    private String registerAndLogin(String email, String password, String nickname) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        rest.exchange("/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", password, "nickname", nickname), h),
                JsonNode.class);
        ResponseEntity<JsonNode> login = rest.exchange("/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(Map.of("email", email, "password", password), h),
                JsonNode.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String setCookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return setCookie.split(";", 2)[0];
    }

    private ResponseEntity<JsonNode> postJson(String url, String cookie, Map<String, ?> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class);
    }

    private ResponseEntity<JsonNode> getJson(String url, String cookie) {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class);
    }

    private ResponseEntity<JsonNode> getJsonArray(String url, String cookie) {
        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, cookie);
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class);
    }
}
