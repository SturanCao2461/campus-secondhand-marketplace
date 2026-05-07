package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import nz.ac.waikato.campusmarketplace.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceLoginLogoutTest {

    UserRepository users;
    PasswordEncoder encoder;
    RateLimitService rateLimit;
    JwtService jwt;
    StringRedisTemplate redis;
    ValueOperations<String, String> valueOps;
    AuthService auth;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        users = mock(UserRepository.class);
        encoder = new BCryptPasswordEncoder();
        rateLimit = mock(RateLimitService.class);
        jwt = mock(JwtService.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        auth = new AuthService(users, encoder, rateLimit, jwt, null, null);
        auth.setRedis(redis);
    }

    @Test
    void loginIssuesTokenForCorrectCredentials() {
        User u = User.builder()
                .id(1L).email("a@students.waikato.ac.nz").nickname("A")
                .password(encoder.encode("Pass1234"))
                .build();
        when(users.findByEmail("a@students.waikato.ac.nz")).thenReturn(Optional.of(u));
        when(jwt.issue(1L, "a@students.waikato.ac.nz", "A"))
                .thenReturn(new JwtService.IssuedToken("tok.abc", "jti-1", Instant.now().plusSeconds(60)));

        AuthService.LoginResult r = auth.login("a@students.waikato.ac.nz", "Pass1234");

        assertEquals("tok.abc", r.token());
        assertEquals(1L, r.user().getId());
    }

    @Test
    void loginRejectsWrongPasswordAndIncrementsRateLimit() {
        User u = User.builder()
                .id(1L).email("a@students.waikato.ac.nz").nickname("A")
                .password(encoder.encode("Pass1234"))
                .build();
        when(users.findByEmail("a@students.waikato.ac.nz")).thenReturn(Optional.of(u));

        ApiException ex = assertThrows(ApiException.class,
                () -> auth.login("a@students.waikato.ac.nz", "WrongPass1"));
        assertEquals(ErrorCode.BAD_CREDENTIALS, ex.getCode());
        verify(rateLimit).increment(eq("ratelimit:login:a@students.waikato.ac.nz"), any());
    }

    @Test
    void loginRejectsUnknownEmailWithSameMessage() {
        when(users.findByEmail("ghost@students.waikato.ac.nz")).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class,
                () -> auth.login("ghost@students.waikato.ac.nz", "Pass1234"));
        assertEquals(ErrorCode.BAD_CREDENTIALS, ex.getCode());
    }

    @Test
    void loginBlockedWhenRateLimitExceeded() {
        when(rateLimit.exceeded(eq("ratelimit:login:a@students.waikato.ac.nz"), eq(5L), any()))
                .thenReturn(true);
        ApiException ex = assertThrows(ApiException.class,
                () -> auth.login("a@students.waikato.ac.nz", "Pass1234"));
        assertEquals(ErrorCode.TOO_MANY_ATTEMPTS, ex.getCode());
    }

    @Test
    void logoutAddsJtiToBlacklist() {
        when(jwt.parse("tok.abc"))
                .thenReturn(new JwtService.ParsedToken(1L, "a@x.nz", "A", "jti-1",
                        Instant.now().plusSeconds(60)));

        auth.logout("tok.abc");

        verify(valueOps).set(eq("jwt:blacklist:jti-1"), eq("1"), any(Duration.class));
    }
}
