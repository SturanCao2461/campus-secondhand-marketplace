package nz.ac.waikato.campusmarketplace.service;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService service;

    @BeforeEach
    void setUp() {
        // 32+ byte base64 secret (DO NOT use this in real code)
        String secret = "VGhpc0lzQVRlc3RTZWNyZXRBdExlYXN0VGhpcnR5VHdvQnl0ZXM=";
        service = new JwtService(secret, 604800);
    }

    @Test
    void issueAndParseRoundTrip() {
        JwtService.IssuedToken issued = service.issue(7L, "alice@students.waikato.ac.nz", "Alice");
        assertNotNull(issued.token());
        assertNotNull(issued.jti());

        JwtService.ParsedToken parsed = service.parse(issued.token());
        assertEquals(7L, parsed.userId());
        assertEquals("alice@students.waikato.ac.nz", parsed.email());
        assertEquals("Alice", parsed.nickname());
        assertEquals(issued.jti(), parsed.jti());
    }

    @Test
    void parseRejectsTamperedToken() {
        JwtService.IssuedToken issued = service.issue(1L, "a@students.waikato.ac.nz", "A");
        // flip last char
        String tampered = issued.token().substring(0, issued.token().length() - 1)
                + (issued.token().endsWith("a") ? "b" : "a");
        assertThrows(JwtException.class, () -> service.parse(tampered));
    }

    @Test
    void remainingTtlPositiveImmediatelyAfterIssue() {
        JwtService.IssuedToken issued = service.issue(1L, "a@students.waikato.ac.nz", "A");
        Duration ttl = service.remainingTtl(issued.token());
        assertTrue(ttl.toSeconds() > 604000, "TTL should be close to full expiry");
    }
}
