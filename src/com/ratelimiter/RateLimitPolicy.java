package com.ratelimiter;

import java.time.Duration;
import java.util.Objects;

/**
 * Token-bucket policy for one customer tier.
 *
 * <p>{@code maxRequests} is the sustained request allowance per {@code window}.
 * {@code burstCapacity} is the maximum number of tokens that can be consumed
 * immediately. It must not exceed {@code maxRequests}.</p>
 */
public record RateLimitPolicy(
        Tier tier,
        long maxRequests,
        Duration window,
        long burstCapacity) {

    public RateLimitPolicy {
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(window, "window");
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be positive");
        }
        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive");
        }
        if (burstCapacity <= 0 || burstCapacity > maxRequests) {
            throw new IllegalArgumentException("burstCapacity must be in [1, maxRequests]");
        }
    }

    /** Tokens added per second. */
    public double refillPerSecond() {
        double seconds = window.getSeconds() + window.getNano() / 1_000_000_000.0d;
        return maxRequests / seconds;
    }
}
