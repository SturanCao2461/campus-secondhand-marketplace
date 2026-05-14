package nz.ac.waikato.campusmarketplace.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirySeconds;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiry-seconds}") long expirySeconds) {
        byte[] bytes = Base64.getDecoder().decode(secret);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 bytes (got " + bytes.length + "). " +
                    "Generate one with: openssl rand -base64 32");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirySeconds = expirySeconds;
    }

    public IssuedToken issue(Long userId, String email, String nickname) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirySeconds);
        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("nickname", nickname)
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new IssuedToken(token, jti, exp);
    }

    public ParsedToken parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return new ParsedToken(
                Long.parseLong(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("nickname", String.class),
                claims.getId(),
                claims.getExpiration().toInstant()
        );
    }

    public Duration remainingTtl(String token) {
        ParsedToken parsed = parse(token);
        return Duration.between(Instant.now(), parsed.expiresAt());
    }

    public long getExpirySeconds() {
        return expirySeconds;
    }

    public record IssuedToken(String token, String jti, Instant expiresAt) {}
    public record ParsedToken(Long userId, String email, String nickname, String jti, Instant expiresAt) {}
}
