package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import nz.ac.waikato.campusmarketplace.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern EMAIL_RE =
            Pattern.compile("^[a-z0-9._%+\\-]+@students\\.waikato\\.ac\\.nz$");
    private static final Pattern PASSWORD_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern PASSWORD_DIGIT = Pattern.compile(".*\\d.*");

    private static final SecureRandom RND = new SecureRandom();

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final RateLimitService rateLimit;
    private final JwtService jwt;
    private final EmailService email;
    private final EmailVerificationService verification;
    private final String emailBaseUrl;

    private StringRedisTemplate redis;

    public AuthService(UserRepository users,
                       PasswordEncoder encoder,
                       RateLimitService rateLimit,
                       JwtService jwt,
                       EmailService email,
                       EmailVerificationService verification,
                       @Value("${app.email.base-url}") String emailBaseUrl) {
        this.users = users;
        this.encoder = encoder;
        this.rateLimit = rateLimit;
        this.jwt = jwt;
        this.email = email;
        this.verification = verification;
        this.emailBaseUrl = emailBaseUrl;
    }

    @Autowired(required = false)
    public void setRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Transactional
    public User register(String rawEmail, String password, String nickname, String ip) {
        RateLimitDecision regDecision = rateLimit.check("ratelimit:register:" + ip, 3, Duration.ofHours(1));
        if (regDecision.exceeded()) {
            throw new ApiException(ErrorCode.TOO_MANY_REGISTRATIONS,
                    "Too many registrations from your network. Try again later.",
                    regDecision.retryAfterSeconds());
        }
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        validateEmail(email);
        validatePassword(password);
        validateNickname(nickname);
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_EXISTS,
                    "This email is already registered. Log in instead?");
        }
        if (users.existsByNickname(nickname)) {
            throw new ApiException(ErrorCode.NICKNAME_TAKEN,
                    "This nickname is taken. Try another.");
        }
        User u = User.builder()
                .email(email)
                .password(encoder.encode(password))
                .nickname(nickname)
                .build();
        User saved = users.save(u);
        rateLimit.increment("ratelimit:register:" + ip, Duration.ofHours(1));
        if (verification != null) {
            verification.issue(saved);
        }
        return saved;
    }

    public LoginResult login(String rawEmail, String password) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        String key = "ratelimit:login:" + email;
        RateLimitDecision loginDecision = rateLimit.check(key, 5, Duration.ofMinutes(15));
        if (loginDecision.exceeded()) {
            throw new ApiException(ErrorCode.TOO_MANY_ATTEMPTS,
                    "Too many attempts. Try again in 15 minutes.",
                    loginDecision.retryAfterSeconds());
        }
        User u = users.findByEmail(email).orElse(null);
        if (u == null || !encoder.matches(password, u.getPassword())) {
            rateLimit.increment(key, Duration.ofMinutes(15));
            throw new ApiException(ErrorCode.BAD_CREDENTIALS,
                    "Email or password is incorrect.");
        }
        JwtService.IssuedToken issued = jwt.issue(u.getId(), u.getEmail(), u.getNickname());
        return new LoginResult(u, issued.token());
    }

    public void logout(String token) {
        if (token == null || redis == null) return;
        JwtService.ParsedToken p = jwt.parse(token);
        Duration remaining = Duration.between(Instant.now(), p.expiresAt());
        if (remaining.isNegative() || remaining.isZero()) return;
        redis.opsForValue().set("jwt:blacklist:" + p.jti(), "1", remaining);
    }

    public boolean isBlacklisted(String jti) {
        if (redis == null) return false;
        return Boolean.TRUE.equals(redis.hasKey("jwt:blacklist:" + jti));
    }

    public User getCurrentUser(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED,
                        "Please log in to continue."));
    }

    public void forgotPassword(String rawEmail) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        User u = users.findByEmail(email).orElse(null);
        if (u == null || redis == null) return;

        byte[] tokenBytes = new byte[48];
        RND.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String key = "auth:reset:" + token;
        redis.opsForValue().set(key, String.valueOf(u.getId()), Duration.ofMinutes(30));

        String link = emailBaseUrl + "/reset-password?token=" + token;
        String body = "We received a request to reset your password.\n\n"
                + "Click the link below within 30 minutes to set a new one:\n"
                + link + "\n\n"
                + "If you didn't request this, ignore this email.";
        this.email.send(u.getEmail(), "Reset your Campus Marketplace password", body);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        if (redis == null) {
            throw new ApiException(ErrorCode.INVALID_TOKEN,
                    "This reset link is invalid or has expired.");
        }
        String key = "auth:reset:" + token;
        String userIdRaw = redis.opsForValue().get(key);
        if (userIdRaw == null) {
            throw new ApiException(ErrorCode.INVALID_TOKEN,
                    "This reset link is invalid or has expired.");
        }
        validatePassword(newPassword);
        Long userId = Long.parseLong(userIdRaw);
        User u = users.findById(userId).orElseThrow(() ->
                new ApiException(ErrorCode.INVALID_TOKEN,
                        "This reset link is invalid or has expired."));
        u.setPassword(encoder.encode(newPassword));
        users.save(u);
        redis.delete(key);
    }

    private void validateEmail(String email) {
        if (!EMAIL_RE.matcher(email).matches()) {
            throw new ApiException(ErrorCode.INVALID_EMAIL,
                    "Only @students.waikato.ac.nz emails are allowed.");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 64
                || !PASSWORD_LETTER.matcher(password).matches()
                || !PASSWORD_DIGIT.matcher(password).matches()) {
            throw new ApiException(ErrorCode.INVALID_PASSWORD,
                    "Password must be 8-64 characters with at least one letter and one digit.");
        }
    }

    private void validateNickname(String nickname) {
        if (nickname == null) {
            throw new ApiException(ErrorCode.INVALID_NICKNAME,
                    "Nickname must be 2-20 characters, no leading/trailing spaces.");
        }
        int len = nickname.length();
        if (len < 2 || len > 20 || !nickname.equals(nickname.trim())) {
            throw new ApiException(ErrorCode.INVALID_NICKNAME,
                    "Nickname must be 2-20 characters, no leading/trailing spaces.");
        }
    }

    public record LoginResult(User user, String token) {}
}
