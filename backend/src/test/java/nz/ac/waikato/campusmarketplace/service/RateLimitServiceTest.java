package nz.ac.waikato.campusmarketplace.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataRedisTest
@Import(RateLimitService.class)
@Testcontainers
class RateLimitServiceTest {

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired RateLimitService service;
    @Autowired StringRedisTemplate redisTemplate;

    @BeforeEach
    void clean() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void incrementCountsUp() {
        assertEquals(1, service.increment("test:k", Duration.ofSeconds(60)));
        assertEquals(2, service.increment("test:k", Duration.ofSeconds(60)));
        assertEquals(3, service.increment("test:k", Duration.ofSeconds(60)));
    }

    @Test
    void exceededReturnsFalseUntilLimit() {
        assertFalse(service.exceeded("test:k", 3, Duration.ofSeconds(60)));
        service.increment("test:k", Duration.ofSeconds(60));
        assertFalse(service.exceeded("test:k", 3, Duration.ofSeconds(60)));
        service.increment("test:k", Duration.ofSeconds(60));
        service.increment("test:k", Duration.ofSeconds(60));
        assertTrue(service.exceeded("test:k", 3, Duration.ofSeconds(60)));
    }

    @Test
    void checkReturnsAllowedDecisionWhenUnderLimit() {
        service.increment("test:underlimit", Duration.ofMinutes(5));
        RateLimitDecision d = service.check("test:underlimit", 5, Duration.ofMinutes(5));
        assertFalse(d.exceeded());
        assertEquals(0L, d.retryAfterSeconds());
    }

    @Test
    void checkReturnsBlockedDecisionWithRetryAfterWhenOver() {
        for (int i = 0; i < 5; i++) {
            service.increment("test:overlimit", Duration.ofMinutes(15));
        }
        RateLimitDecision d = service.check("test:overlimit", 5, Duration.ofMinutes(15));
        assertTrue(d.exceeded());
        // TTL must be > 0 and not greater than the 15-minute window.
        assertTrue(d.retryAfterSeconds() > 0L);
        assertTrue(d.retryAfterSeconds() <= 900L);
    }

    @Test
    void incrementByAddsBytesAndCheckRespectsByteLimit() {
        service.incrementBy("test:bytes", 5_000_000L, Duration.ofHours(1));
        service.incrementBy("test:bytes", 3_000_000L, Duration.ofHours(1));
        RateLimitDecision under = service.check("test:bytes", 50_000_000L, Duration.ofHours(1));
        assertFalse(under.exceeded()); // 8 MB < 50 MB

        service.incrementBy("test:bytes", 43_000_000L, Duration.ofHours(1));
        RateLimitDecision over = service.check("test:bytes", 50_000_000L, Duration.ofHours(1));
        assertTrue(over.exceeded()); // 51 MB >= 50 MB
        assertNotNull(over.retryAfterSeconds());
    }
}
