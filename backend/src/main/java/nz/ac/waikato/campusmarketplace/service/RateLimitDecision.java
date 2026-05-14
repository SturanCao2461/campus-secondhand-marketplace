package nz.ac.waikato.campusmarketplace.service;

public record RateLimitDecision(boolean exceeded, long retryAfterSeconds) {
    public static RateLimitDecision allowed() {
        return new RateLimitDecision(false, 0L);
    }

    public static RateLimitDecision blocked(long retryAfterSeconds) {
        return new RateLimitDecision(true, retryAfterSeconds);
    }
}
