package nz.ac.waikato.campusmarketplace.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nz.ac.waikato.campusmarketplace.AbstractIntegrationTest;
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

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository users;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper json;

    @BeforeEach
    void clean() {
        users.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void registerHappyPath() {
        ResponseEntity<JsonNode> r = postJson("/api/auth/register", Map.of(
                "email", "alice@students.waikato.ac.nz",
                "password", "Pass1234",
                "nickname", "Alice"
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody().get("email").asText()).isEqualTo("alice@students.waikato.ac.nz");
        assertThat(r.getBody().get("nickname").asText()).isEqualTo("Alice");
    }

    @Test
    void registerRejectsNonCampusDomain() {
        ResponseEntity<JsonNode> r = postJson("/api/auth/register", Map.of(
                "email", "alice@gmail.com",
                "password", "Pass1234",
                "nickname", "Alice"
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().get("code").asText()).isEqualTo("INVALID_EMAIL");
    }

    @Test
    void loginSetsHttpOnlyCookieAndDoesNotReturnTokenInBody() {
        register("bob@students.waikato.ac.nz", "Pass1234", "Bob");

        ResponseEntity<JsonNode> r = postJson("/api/auth/login", Map.of(
                "email", "bob@students.waikato.ac.nz",
                "password", "Pass1234"
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("user").get("nickname").asText()).isEqualTo("Bob");
        assertThat(r.getBody().has("token")).isFalse();

        String setCookie = r.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Lax");
        assertThat(setCookie).startsWith("token=");
    }

    @Test
    void loginRejectsWrongPasswordWithGenericMessage() {
        register("c@students.waikato.ac.nz", "Pass1234", "Carol");
        ResponseEntity<JsonNode> r = postJson("/api/auth/login", Map.of(
                "email", "c@students.waikato.ac.nz",
                "password", "WrongPass1"
        ));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getBody().get("code").asText()).isEqualTo("BAD_CREDENTIALS");
    }

    @Test
    void meRequiresAuthAndReturnsCurrentUser() {
        ResponseEntity<JsonNode> unauth = rest.getForEntity("/api/auth/me", JsonNode.class);
        assertThat(unauth.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        register("dan@students.waikato.ac.nz", "Pass1234", "Dan");
        String cookie = loginAndGetCookie("dan@students.waikato.ac.nz", "Pass1234");

        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, cookie);
        ResponseEntity<JsonNode> me = rest.exchange(
                "/api/auth/me", HttpMethod.GET, new HttpEntity<>(h), JsonNode.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("nickname").asText()).isEqualTo("Dan");
    }

    @Test
    void logoutBlacklistsTokenSoSubsequentMeFails() {
        register("e@students.waikato.ac.nz", "Pass1234", "Eve");
        String cookie = loginAndGetCookie("e@students.waikato.ac.nz", "Pass1234");

        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, cookie);

        ResponseEntity<Void> logout = rest.exchange(
                "/api/auth/logout", HttpMethod.POST, new HttpEntity<>(h), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<JsonNode> me = rest.exchange(
                "/api/auth/me", HttpMethod.GET, new HttpEntity<>(h), JsonNode.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void forgotPasswordAlwaysReturns204() {
        ResponseEntity<Void> existing = postJson("/api/auth/forgot-password",
                Map.of("email", "ghost@students.waikato.ac.nz"), Void.class);
        assertThat(existing.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        register("real@students.waikato.ac.nz", "Pass1234", "Real");
        ResponseEntity<Void> real = postJson("/api/auth/forgot-password",
                Map.of("email", "real@students.waikato.ac.nz"), Void.class);
        assertThat(real.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void resetPasswordEndToEnd() {
        register("f@students.waikato.ac.nz", "OldPass1", "Frank");
        postJson("/api/auth/forgot-password",
                Map.of("email", "f@students.waikato.ac.nz"), Void.class);

        Set<String> keys = redis.keys("auth:reset:*");
        assertThat(keys).isNotEmpty();
        String fullKey = keys.iterator().next();
        String token = fullKey.substring("auth:reset:".length());

        ResponseEntity<JsonNode> reset = postJson("/api/auth/reset-password", Map.of(
                "token", token,
                "newPassword", "NewPass1"
        ));
        assertThat(reset.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> login = postJson("/api/auth/login", Map.of(
                "email", "f@students.waikato.ac.nz",
                "password", "NewPass1"
        ));
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<JsonNode> postJson(String path, Map<String, String> body) {
        return postJson(path, body, JsonNode.class);
    }

    private <T> ResponseEntity<T> postJson(String path, Map<String, String> body, Class<T> type) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, h), type);
    }

    private void register(String email, String password, String nickname) {
        ResponseEntity<JsonNode> r = postJson("/api/auth/register",
                Map.of("email", email, "password", password, "nickname", nickname));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String loginAndGetCookie(String email, String password) {
        ResponseEntity<JsonNode> r = postJson("/api/auth/login",
                Map.of("email", email, "password", password));
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        String setCookie = r.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).isNotNull();
        return setCookie.split(";", 2)[0];
    }
}
