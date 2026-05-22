package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import nz.ac.waikato.campusmarketplace.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AuthServiceResetTest {

    UserRepository users;
    PasswordEncoder encoder;
    EmailService email;
    StringRedisTemplate redis;
    ValueOperations<String, String> valueOps;
    AuthService auth;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        users = mock(UserRepository.class);
        encoder = new BCryptPasswordEncoder();
        email = mock(EmailService.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        auth = new AuthService(users, encoder, mock(RateLimitService.class), null, email, null,
                "http://localhost:5173");
        auth.setRedis(redis);
    }

    @Test
    void forgotPasswordSendsEmailIfUserExists() {
        User u = User.builder().id(1L).email("a@students.waikato.ac.nz").nickname("A")
                .password("h").build();
        when(users.findByEmail("a@students.waikato.ac.nz")).thenReturn(Optional.of(u));

        auth.forgotPassword("a@students.waikato.ac.nz");

        ArgumentCaptor<String> tokenCap = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(tokenCap.capture(), eq("1"), eq(Duration.ofMinutes(30)));
        assertTrue(tokenCap.getValue().startsWith("auth:reset:"));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(email).send(eq("a@students.waikato.ac.nz"),
                eq("Reset your Campus Marketplace password"), body.capture());
        assertTrue(body.getValue().contains("http://localhost:5173/reset-password?token="));
    }

    @Test
    void forgotPasswordSilentIfUserMissing() {
        when(users.findByEmail("ghost@students.waikato.ac.nz")).thenReturn(Optional.empty());
        auth.forgotPassword("ghost@students.waikato.ac.nz");
        verifyNoInteractions(email);
        verifyNoInteractions(valueOps);
    }

    @Test
    void resetPasswordWithValidTokenUpdatesPassword() {
        User u = User.builder().id(1L).email("a@x.nz").nickname("A")
                .password(encoder.encode("OldPass1")).build();
        when(redis.opsForValue().get("auth:reset:tok123")).thenReturn("1");
        when(users.findById(1L)).thenReturn(Optional.of(u));
        when(users.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        auth.resetPassword("tok123", "NewPass1");

        verify(redis).delete("auth:reset:tok123");
        assertTrue(encoder.matches("NewPass1", u.getPassword()));
    }

    @Test
    void resetPasswordRejectsInvalidToken() {
        when(redis.opsForValue().get("auth:reset:bad")).thenReturn(null);
        ApiException ex = assertThrows(ApiException.class,
                () -> auth.resetPassword("bad", "NewPass1"));
        assertEquals(ErrorCode.INVALID_TOKEN, ex.getCode());
    }

    @Test
    void resetPasswordRejectsWeakPassword() {
        when(redis.opsForValue().get("auth:reset:tok123")).thenReturn("1");
        ApiException ex = assertThrows(ApiException.class,
                () -> auth.resetPassword("tok123", "short"));
        assertEquals(ErrorCode.INVALID_PASSWORD, ex.getCode());
    }
}
