package nz.ac.waikato.campusmarketplace.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

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

    public boolean exceeded(String key, long limit, Duration window) {
        String raw = redis.opsForValue().get(key);
        if (raw == null) return false;
        return Long.parseLong(raw) >= limit;
    }
}
