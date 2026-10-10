package com.ratelimiter;

/**
 * Atomic token-bucket storage contract.
 *
 * <p>A distributed implementation must perform refill and consume as one
 * atomic operation (for example, with a Redis Lua script). Do not implement
 * this contract as separate read and write calls.</p>
 */
public interface RateLimitStore extends AutoCloseable {
    StoreResult consume(String key, RateLimitPolicy policy, int cost, long nowNanos);

    @Override
    default void close() {
        // Stores without resources do not need shutdown work.
    }

    record StoreResult(boolean allowed, double tokensRemaining,
                       double retryAfterSeconds, double resetAfterSeconds) {
        public StoreResult {
            if (!Double.isFinite(tokensRemaining) || !Double.isFinite(retryAfterSeconds)
                    || !Double.isFinite(resetAfterSeconds)
                    || tokensRemaining < 0 || retryAfterSeconds < 0 || resetAfterSeconds < 0) {
                throw new IllegalArgumentException("store result values cannot be negative");
            }
        }
    }
}
