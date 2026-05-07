package nz.ac.waikato.campusmarketplace.service;

import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import nz.ac.waikato.campusmarketplace.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
        boolean badEdges = !nickname.equals(nickname.trim());
        if (len < 2 || len > 20 || badEdges) {
            throw new ApiException(ErrorCode.INVALID_NICKNAME,
                    "Nickname must be 2-20 characters, no leading/trailing spaces.");
        }
    }
}
