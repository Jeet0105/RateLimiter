package com.ratelimiter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable result of a rate-limit check. */
public record RateLimitDecision(
        boolean allowed,
        Tier tier,
        long limit,
        long remaining,
        Instant resetAt,
        long resetAfterSeconds,
        long retryAfterSeconds) {

    public RateLimitDecision {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(resetAt, "resetAt");
        if (limit <= 0 || remaining < 0 || remaining > limit) {
            throw new IllegalArgumentException("invalid rate-limit values");
        }
        if (resetAfterSeconds < 0 || retryAfterSeconds < 0) {
            throw new IllegalArgumentException("reset/retry seconds cannot be negative");
        }
    }

    /** Headers suitable for an HTTP response. */
    public Map<String, String> headers() {
        var headers = new LinkedHashMap<String, String>();
        headers.put("RateLimit-Limit", Long.toString(limit));
        headers.put("RateLimit-Remaining", Long.toString(remaining));
        // RFC 9448 defines RateLimit-Reset as a delta. The X- header is kept
        // as an absolute timestamp for clients using the older convention.
        headers.put("RateLimit-Reset", Long.toString(resetAfterSeconds));
        headers.put("X-RateLimit-Reset", Long.toString(resetAt.getEpochSecond()));
        if (!allowed) {
            headers.put("Retry-After", Long.toString(Math.max(1, retryAfterSeconds)));
        }
        return Map.copyOf(headers);
    }
}
