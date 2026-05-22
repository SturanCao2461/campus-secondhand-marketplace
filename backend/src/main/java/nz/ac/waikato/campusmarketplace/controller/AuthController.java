package nz.ac.waikato.campusmarketplace.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import nz.ac.waikato.campusmarketplace.dto.ForgotPasswordRequest;
import nz.ac.waikato.campusmarketplace.dto.LoginRequest;
import nz.ac.waikato.campusmarketplace.dto.RegisterRequest;
import nz.ac.waikato.campusmarketplace.dto.ResetPasswordRequest;
import nz.ac.waikato.campusmarketplace.dto.UserResponse;
import nz.ac.waikato.campusmarketplace.entity.User;
import nz.ac.waikato.campusmarketplace.exception.ApiException;
import nz.ac.waikato.campusmarketplace.exception.ErrorCode;
import nz.ac.waikato.campusmarketplace.filter.AuthPrincipal;
import nz.ac.waikato.campusmarketplace.service.AuthService;
import nz.ac.waikato.campusmarketplace.service.EmailVerificationService;
import nz.ac.waikato.campusmarketplace.service.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final EmailVerificationService verification;
    private final JwtService jwt;
    private final String cookieName;
    private final boolean cookieSecure;
    private final String cookieSameSite;

    public AuthController(AuthService auth, EmailVerificationService verification, JwtService jwt,
                          @Value("${app.cookie.name}") String cookieName,
                          @Value("${app.cookie.secure}") boolean cookieSecure,
                          @Value("${app.cookie.same-site}") String cookieSameSite) {
        this.auth = auth;
        this.verification = verification;
        this.jwt = jwt;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
        this.cookieSameSite = cookieSameSite;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req,
                                                 HttpServletRequest http) {
        User u = auth.register(req.email(), req.password(), req.nickname(), clientIp(http));
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(u));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest req,
                                                     HttpServletResponse res) {
        AuthService.LoginResult r = auth.login(req.email(), req.password());
        setTokenCookie(res, r.token(), (int) jwt.getExpirySeconds());
        return ResponseEntity.ok(Map.of("user", UserResponse.from(r.user())));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req, HttpServletResponse res,
                                       Authentication authn) {
        if (authn == null || !authn.isAuthenticated()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Please log in to continue.");
        }
        String token = null;
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if (cookieName.equals(c.getName())) {
                    token = c.getValue();
                    break;
                }
            }
        }
        auth.logout(token);
        clearTokenCookie(res);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal AuthPrincipal principal) {
        if (principal == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Please log in to continue.");
        }
        User u = auth.getCurrentUser(principal.userId());
        return ResponseEntity.ok(UserResponse.from(u));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgot(@Valid @RequestBody ForgotPasswordRequest req) {
        auth.forgotPassword(req.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> reset(@Valid @RequestBody ResetPasswordRequest req) {
        auth.resetPassword(req.token(), req.newPassword());
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestBody Map<String, String> body) {
        String token = body == null ? null : body.get("token");
        verification.consume(token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@AuthenticationPrincipal AuthPrincipal principal) {
        if (principal == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Please log in to continue.");
        }
        verification.resend(principal.userId());
        return ResponseEntity.noContent().build();
    }

    private void setTokenCookie(HttpServletResponse res, String token, int maxAgeSeconds) {
        String cookie = String.format(
                "%s=%s; Max-Age=%d; Path=/; HttpOnly; SameSite=%s%s",
                cookieName, token, maxAgeSeconds, cookieSameSite,
                cookieSecure ? "; Secure" : "");
        res.addHeader("Set-Cookie", cookie);
    }

    private void clearTokenCookie(HttpServletResponse res) {
        String cookie = String.format(
                "%s=; Max-Age=0; Path=/; HttpOnly; SameSite=%s%s",
                cookieName, cookieSameSite, cookieSecure ? "; Secure" : "");
        res.addHeader("Set-Cookie", cookie);
    }

    private String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
