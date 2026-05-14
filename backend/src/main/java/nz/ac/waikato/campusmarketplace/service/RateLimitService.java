package nz.ac.waikato.campusmarketplace.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {

    private final StringRedisTemplate redis;

    public RateLimitService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public long increment(String key, Duration window) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, window);
        }
        return count == null ? 0 : count;
    }

    public long incrementBy(String key, long delta, Duration window) {
        Long total = redis.opsForValue().increment(key, delta);
        if (total != null && total == delta) {
            redis.expire(key, window);
        }
        return total == null ? 0 : total;
    }

    /**
     * TTL-aware decision API. Returns {@link RateLimitDecision#blocked(long)} when the current
     * value is at or above {@code limit}, with retryAfterSeconds equal to the remaining TTL on
     * the Redis key. Returns {@link RateLimitDecision#allowed()} otherwise.
     */
    public RateLimitDecision check(String key, long limit, Duration window) {
        String raw = redis.opsForValue().get(key);
        if (raw == null) return RateLimitDecision.allowed();
        long current = Long.parseLong(raw);
        if (current < limit) return RateLimitDecision.allowed();
        Long ttlSeconds = redis.getExpire(key, TimeUnit.SECONDS);
        long retry = (ttlSeconds == null || ttlSeconds <= 0L) ? window.toSeconds() : ttlSeconds;
        return RateLimitDecision.blocked(retry);
    }

    /** Legacy boolean API — thin delegate over {@link #check}. */
    public boolean exceeded(String key, long limit, Duration window) {
        return check(key, limit, window).exceeded();
    }
}
