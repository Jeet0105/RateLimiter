package com.ratelimiter;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/** Default plans. Applications can provide their own policies to the constructor. */
public final class RateLimitPolicies {
    private RateLimitPolicies() {
    }

    public static Map<Tier, RateLimitPolicy> defaults() {
        var policies = new EnumMap<Tier, RateLimitPolicy>(Tier.class);
        policies.put(Tier.FREE, new RateLimitPolicy(Tier.FREE, 60, Duration.ofMinutes(1), 10));
        policies.put(Tier.PREMIUM, new RateLimitPolicy(Tier.PREMIUM, 600, Duration.ofMinutes(1), 100));
        return Map.copyOf(policies);
    }
}
