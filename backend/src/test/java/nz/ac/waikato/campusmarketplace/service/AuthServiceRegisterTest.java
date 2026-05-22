package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import nz.ac.waikato.campusmarketplace.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceRegisterTest {

    UserRepository users;
    PasswordEncoder encoder;
    RateLimitService rateLimit;
    AuthService auth;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = new BCryptPasswordEncoder();
        rateLimit = mock(RateLimitService.class);
        when(rateLimit.check(any(), any(Long.class), any())).thenReturn(RateLimitDecision.allowed());
        auth = new AuthService(users, encoder, rateLimit, null, null, null, null);
    }

    @Test
    void registersValidUser() {
        when(users.existsByEmail("alice@students.waikato.ac.nz")).thenReturn(false);
        when(users.existsByNickname("Alice")).thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        User saved = auth.register("Alice@Students.Waikato.AC.NZ", "Pass1234", "Alice", "1.2.3.4");

        assertEquals(1L, saved.getId());
        assertEquals("alice@students.waikato.ac.nz", saved.getEmail());
        assertEquals("Alice", saved.getNickname());
        assertNotEquals("Pass1234", saved.getPassword());
        assertTrue(encoder.matches("Pass1234", saved.getPassword()));
    }

    @Test
    void rejectsInvalidEmailDomain() {
        ApiException ex = assertThrows(ApiException.class,
                () -> auth.register("alice@gmail.com", "Pass1234", "Alice", "1.2.3.4"));
        assertEquals(ErrorCode.INVALID_EMAIL, ex.getCode());
    }

    @Test
    void rejectsWeakPassword() {
        ApiException ex = assertThrows(ApiException.class,
                () -> auth.register("a@students.waikato.ac.nz", "short", "Alice", "1.2.3.4"));
        assertEquals(ErrorCode.INVALID_PASSWORD, ex.getCode());
    }

    @Test
    void rejectsBadNickname() {
        ApiException ex = assertThrows(ApiException.class,
                () -> auth.register("a@students.waikato.ac.nz", "Pass1234", "A", "1.2.3.4"));
        assertEquals(ErrorCode.INVALID_NICKNAME, ex.getCode());
    }

    @Test
    void rejectsExistingEmail() {
        when(users.existsByEmail("a@students.waikato.ac.nz")).thenReturn(true);
        ApiException ex = assertThrows(ApiException.class,
                () -> auth.register("a@students.waikato.ac.nz", "Pass1234", "Alice", "1.2.3.4"));
        assertEquals(ErrorCode.EMAIL_EXISTS, ex.getCode());
    }

    @Test
    void rejectsTakenNickname() {
        when(users.existsByEmail("a@students.waikato.ac.nz")).thenReturn(false);
        when(users.existsByNickname("Alice")).thenReturn(true);
        ApiException ex = assertThrows(ApiException.class,
                () -> auth.register("a@students.waikato.ac.nz", "Pass1234", "Alice", "1.2.3.4"));
        assertEquals(ErrorCode.NICKNAME_TAKEN, ex.getCode());
    }

    @Test
    void rejectsRateLimitedIp() {
        when(rateLimit.check(eq("ratelimit:register:1.2.3.4"), eq(3L), any()))
                .thenReturn(RateLimitDecision.blocked(3600L));
        ApiException ex = assertThrows(ApiException.class,
                () -> auth.register("a@students.waikato.ac.nz", "Pass1234", "Alice", "1.2.3.4"));
        assertEquals(ErrorCode.TOO_MANY_REGISTRATIONS, ex.getCode());
        assertEquals(3600L, ex.getRetryAfterSeconds());
    }
}
