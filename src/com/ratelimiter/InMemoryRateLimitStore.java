package com.ratelimiter;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, bounded in-memory token-bucket store.
 *
 * <p>This is appropriate for a single JVM or as a local first-line limiter.
 * Its bounded key admission prevents an attacker from making the process grow
 * without limit. For multiple application instances, use a shared atomic store
 * implementing {@link RateLimitStore}.</p>
 */
public final class InMemoryRateLimitStore implements RateLimitStore {
    private static final long DEFAULT_MAX_BUCKETS = 100_000;
    private static final long DEFAULT_IDLE_NANOS = 15 * 60 * 1_000_000_000L;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final long maxBuckets;
    private final long idleNanos;
    private final Object admissionLock = new Object();

    public InMemoryRateLimitStore() {
        this(DEFAULT_MAX_BUCKETS, DEFAULT_IDLE_NANOS);
    }

    public InMemoryRateLimitStore(long maxBuckets, long idleNanos) {
        if (maxBuckets <= 0) {
            throw new IllegalArgumentException("maxBuckets must be positive");
        }
        if (idleNanos <= 0) {
            throw new IllegalArgumentException("idleNanos must be positive");
        }
        this.maxBuckets = maxBuckets;
        this.idleNanos = idleNanos;
    }

    @Override
    public StoreResult consume(String key, RateLimitPolicy policy, int cost, long nowNanos) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(policy, "policy");
        if (cost <= 0) {
            throw new IllegalArgumentException("cost must be positive");
        }

        Bucket bucket = bucketFor(key, policy, nowNanos);
        synchronized (bucket) {
            return bucket.consume(policy, cost, nowNanos);
        }
    }

    /** Removes inactive buckets. Call periodically if traffic is very sparse. */
    public void cleanup(long nowNanos) {
        for (var entry : buckets.entrySet()) {
            Bucket bucket = entry.getValue();
            synchronized (bucket) {
                if (bucket.isIdle(nowNanos, idleNanos)) {
                    buckets.remove(entry.getKey(), bucket);
                }
            }
        }
    }

    public int size() {
        return buckets.size();
    }

    private Bucket bucketFor(String key, RateLimitPolicy policy, long nowNanos) {
        Bucket bucket = buckets.get(key);
        if (bucket != null) {
            return bucket;
        }

        synchronized (admissionLock) {
            bucket = buckets.get(key);
            if (bucket != null) {
                return bucket;
            }
            cleanup(nowNanos);
            if (buckets.size() >= maxBuckets) {
                throw new StoreCapacityExceededException("rate-limit bucket capacity exhausted");
            }
            Bucket created = new Bucket(policy, nowNanos);
            buckets.put(key, created);
            return created;
        }
    }

    private static final class Bucket {
        private RateLimitPolicy policy;
        private double tokens;
        private long lastRefillNanos;
        private volatile long lastAccessNanos;

        private Bucket(RateLimitPolicy policy, long nowNanos) {
            this.policy = policy;
            this.tokens = policy.burstCapacity();
            this.lastRefillNanos = nowNanos;
            this.lastAccessNanos = nowNanos;
        }

        private StoreResult consume(RateLimitPolicy requestedPolicy, int cost, long nowNanos) {
            if (!policy.equals(requestedPolicy)) {
                refill(nowNanos);
                boolean upgrade = requestedPolicy.maxRequests() > policy.maxRequests()
                        || requestedPolicy.burstCapacity() > policy.burstCapacity();
                tokens = upgrade
                        ? requestedPolicy.burstCapacity()
                        : Math.min(tokens, requestedPolicy.burstCapacity());
                policy = requestedPolicy;
                lastRefillNanos = nowNanos;
            }

            refill(nowNanos);
            lastAccessNanos = nowNanos;
            if (cost > policy.burstCapacity()) {
                return new StoreResult(false, floor(tokens), 0, secondsToFull());
            }
            if (tokens >= cost) {
                tokens -= cost;
                return new StoreResult(true, floor(tokens), 0, secondsToFull());
            }
            double missing = cost - tokens;
            return new StoreResult(false, floor(tokens), ceilSeconds(missing / policy.refillPerSecond()),
                    secondsToFull());
        }

        private void refill(long nowNanos) {
            long elapsedNanos = nowNanos - lastRefillNanos;
            if (elapsedNanos <= 0) {
                return;
            }
            double added = elapsedNanos / 1_000_000_000.0d * policy.refillPerSecond();
            tokens = Math.min(policy.burstCapacity(), tokens + added);
            lastRefillNanos = nowNanos;
        }

        private double secondsToFull() {
            return ceilSeconds((policy.burstCapacity() - tokens) / policy.refillPerSecond());
        }

        private boolean isIdle(long nowNanos, long idleNanos) {
            return nowNanos - lastAccessNanos > idleNanos;
        }

        private static double floor(double value) {
            return Math.max(0, Math.floor(value));
        }

        private static long ceilSeconds(double value) {
            if (value <= 0) {
                return 0;
            }
            return Math.max(1, (long) Math.ceil(value));
        }
    }

    public static final class StoreCapacityExceededException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public StoreCapacityExceededException(String message) {
            super(message);
        }
    }
}
