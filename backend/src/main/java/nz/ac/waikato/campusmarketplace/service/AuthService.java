package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import nz.ac.waikato.campusmarketplace.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern EMAIL_RE =
            Pattern.compile("^[a-z0-9._%+\\-]+@students\\.waikato\\.ac\\.nz$");
    private static final Pattern PASSWORD_LETTER = Pattern.compile(".*[A-Za-z].*");
    private static final Pattern PASSWORD_DIGIT = Pattern.compile(".*\\d.*");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final RateLimitService rateLimit;
    private final JwtService jwt;
    private final EmailService email;
    private final String emailBaseUrl;

    private StringRedisTemplate redis;

    public AuthService(UserRepository users,
                       PasswordEncoder encoder,
                       RateLimitService rateLimit,
                       JwtService jwt,
                       EmailService email,
                       String emailBaseUrl) {
        this.users = users;
        this.encoder = encoder;
        this.rateLimit = rateLimit;
        this.jwt = jwt;
        this.email = email;
        this.emailBaseUrl = emailBaseUrl;
    }

    @Autowired(required = false)
    public void setRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Transactional
    public User register(String rawEmail, String password, String nickname, String ip) {
        if (rateLimit.exceeded("ratelimit:register:" + ip, 3, Duration.ofHours(1))) {
            throw new ApiException(ErrorCode.TOO_MANY_REGISTRATIONS,
                    "Too many registrations from your network. Try again later.");
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
        return saved;
    }

    public LoginResult login(String rawEmail, String password) {
        String email = rawEmail == null ? "" : rawEmail.trim().toLowerCase();
        String key = "ratelimit:login:" + email;
        if (rateLimit.exceeded(key, 5, Duration.ofMinutes(15))) {
            throw new ApiException(ErrorCode.TOO_MANY_ATTEMPTS,
                    "Too many attempts. Try again in 15 minutes.");
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
