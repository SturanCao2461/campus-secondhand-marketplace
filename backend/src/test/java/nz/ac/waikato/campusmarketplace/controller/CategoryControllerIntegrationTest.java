package nz.ac.waikato.campusmarketplace.controller;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;

class CategoryControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired UserRepository users;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        users.deleteAll();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void unauthenticatedRequestReturnsCategories() {
        // Categories are now public (Epic 3: browse page needs them without login)
        ResponseEntity<JsonNode> r = rest.getForEntity("/api/categories", JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("items").size()).isEqualTo(8);
    }

    @Test
    void authenticatedRequestReturnsSeededCategoriesInOrder() {
        register("cat-test@students.waikato.ac.nz", "Pass1234", "CatTest");
        String cookie = loginAndGetCookie("cat-test@students.waikato.ac.nz", "Pass1234");

        HttpHeaders h = new HttpHeaders();
        h.add(HttpHeaders.COOKIE, cookie);
        ResponseEntity<JsonNode> r = rest.exchange(
                "/api/categories", HttpMethod.GET, new HttpEntity<>(h), JsonNode.class);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode items = r.getBody().get("items");
        assertThat(items.isArray()).isTrue();
        assertThat(items.size()).isEqualTo(8);

        // First should be BOOKS (sortOrder 10), last should be OTHER (sortOrder 99).
        assertThat(items.get(0).get("code").asText()).isEqualTo("BOOKS");
        assertThat(items.get(7).get("code").asText()).isEqualTo("OTHER");

        // Each entry has the three required fields.
        assertThat(items.get(0).get("nameEn").asText()).isEqualTo("Books & Textbooks");
        assertThat(items.get(0).get("nameZh").asText()).isEqualTo("书籍教材");
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
}
