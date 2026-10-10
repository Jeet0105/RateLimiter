package com.ratelimiter;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Production-facing facade for tier-aware rate-limit checks. */
public final class RateLimiter {
    private static final int DEFAULT_MAX_COST = 10_000;
    private static final int MAX_KEY_LENGTH = 256;

    private final RateLimitStore store;
    private final Map<Tier, RateLimitPolicy> policies;
    private final TimeSource timeSource;
    private final int maxCost;

    public RateLimiter(RateLimitStore store, Map<Tier, RateLimitPolicy> policies) {
        this(store, policies, TimeSource.SYSTEM, DEFAULT_MAX_COST);
    }

    public RateLimiter(RateLimitStore store, Map<Tier, RateLimitPolicy> policies,
                       TimeSource timeSource, int maxCost) {
        this.store = Objects.requireNonNull(store, "store");
        Objects.requireNonNull(policies, "policies");
        this.policies = new EnumMap<>(Tier.class);
        for (Tier tier : Tier.values()) {
            RateLimitPolicy policy = policies.get(tier);
            if (policy == null || policy.tier() != tier) {
                throw new IllegalArgumentException("a policy is required for every tier");
            }
            this.policies.put(tier, policy);
        }
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
        if (maxCost <= 0) {
            throw new IllegalArgumentException("maxCost must be positive");
        }
        this.maxCost = maxCost;
    }

    public RateLimitDecision check(String key, Tier tier) {
        return check(key, tier, 1);
    }

    /** Checks and, when allowed, atomically consumes {@code cost} tokens. */
    public RateLimitDecision check(String key, Tier tier, int cost) {
        validateKey(key);
        Objects.requireNonNull(tier, "tier");
        if (cost <= 0 || cost > maxCost) {
            throw new IllegalArgumentException("cost must be between 1 and " + maxCost);
        }

        RateLimitPolicy policy = policies.get(tier);
        Instant now = timeSource.now();
        RateLimitStore.StoreResult result;
        try {
            result = store.consume(bucketKey(key, tier), policy, cost, timeSource.nanoTime());
        } catch (InMemoryRateLimitStore.StoreCapacityExceededException e) {
            // Fail closed when an untrusted caller exhausts local bucket capacity.
            return new RateLimitDecision(false, tier, policy.maxRequests(), 0,
                    now.plusSeconds(1), 1, 1);
        }

        long remaining = Math.min(policy.maxRequests(), Math.max(0,
                (long) Math.floor(result.tokensRemaining())));
        long resetSeconds = Math.max(0, (long) Math.ceil(result.resetAfterSeconds()));
        long retrySeconds = Math.max(0, (long) Math.ceil(result.retryAfterSeconds()));
        return new RateLimitDecision(result.allowed(), tier, policy.maxRequests(), remaining,
                now.plusSeconds(resetSeconds), resetSeconds, retrySeconds);
    }

    private static void validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException("key must be non-blank and at most " + MAX_KEY_LENGTH + " characters");
        }
        for (int i = 0; i < key.length(); i++) {
            if (Character.isISOControl(key.charAt(i))) {
                throw new IllegalArgumentException("key cannot contain control characters");
            }
        }
    }

    private static String bucketKey(String key, Tier tier) {
        // Keep plan buckets independent. This makes a legitimate plan upgrade
        // immediate and prevents alternating plan values from resetting a bucket.
        return tier.name() + ":" + key;
    }
}
