package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import nz.ac.waikato.campusmarketplace.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Issues, validates, and consumes email-verification tokens.
 *
 * Tokens are 48 random bytes, URL-safe base64 encoded, stored in Redis with a
 * 24-hour TTL. Same pattern as password reset (D-21) — short-lived,
 * high-cardinality, native expiration. The link the user clicks is
 * `{base-url}/verify-email?token=...`; the frontend page POSTs the token to
 * `/api/auth/verify-email`, which calls {@link #consume}.
 */
@Service
public class EmailVerificationService {

    private static final SecureRandom RND = new SecureRandom();
    private static final String REDIS_KEY_PREFIX = "auth:verify:";
    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final UserRepository users;
    private final EmailService email;
    private final String emailBaseUrl;

    private StringRedisTemplate redis;

    public EmailVerificationService(UserRepository users,
                                    EmailService email,
                                    @Value("${app.email.base-url}") String emailBaseUrl) {
        this.users = users;
        this.email = email;
        this.emailBaseUrl = emailBaseUrl;
    }

    @Autowired(required = false)
    public void setRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * Generate a fresh verification token for {@code user} and email the link.
     * No-op if Redis is unavailable (degraded mode — never blocks registration).
     */
    public void issue(User user) {
        if (redis == null) return;
        if (user.isEmailVerified()) return;

        byte[] tokenBytes = new byte[48];
        RND.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        redis.opsForValue().set(REDIS_KEY_PREFIX + token, String.valueOf(user.getId()), TOKEN_TTL);

        String link = emailBaseUrl + "/verify-email?token=" + token;
        String body = "Welcome to Campus Marketplace!\n\n"
                + "Click the link below within 24 hours to verify your email:\n"
                + link + "\n\n"
                + "If you didn't create an account, ignore this email.";
        email.send(user.getEmail(), "Verify your Campus Marketplace email", body);
    }

    /**
     * Consume a verification token: mark the matching user as verified and delete
     * the token. Throws {@link ApiException} with {@link ErrorCode#INVALID_VERIFICATION_TOKEN}
     * if the token is missing, expired, or maps to a non-existent user.
     */
    @Transactional
    public void consume(String token) {
        if (redis == null || token == null || token.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_VERIFICATION_TOKEN,
                    "This verification link is invalid or has expired.");
        }
        String key = REDIS_KEY_PREFIX + token;
        String userIdRaw = redis.opsForValue().get(key);
        if (userIdRaw == null) {
            throw new ApiException(ErrorCode.INVALID_VERIFICATION_TOKEN,
                    "This verification link is invalid or has expired.");
        }
        Long userId = Long.parseLong(userIdRaw);
        User u = users.findById(userId).orElseThrow(() ->
                new ApiException(ErrorCode.INVALID_VERIFICATION_TOKEN,
                        "This verification link is invalid or has expired."));
        u.setEmailVerified(true);
        users.save(u);
        redis.delete(key);
    }

    /**
     * Re-issue a verification token for the currently-logged-in user.
     * Idempotent — calling it on a verified user is a no-op (no email sent).
     */
    public void resend(Long userId) {
        User u = users.findById(userId).orElseThrow(() ->
                new ApiException(ErrorCode.UNAUTHENTICATED, "Please log in to continue."));
        issue(u);
    }
}
