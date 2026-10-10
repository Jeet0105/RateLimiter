import com.ratelimiter.InMemoryRateLimitStore;
import com.ratelimiter.RateLimitDecision;
import com.ratelimiter.RateLimitPolicies;
import com.ratelimiter.RateLimiter;
import com.ratelimiter.TimeSource;
import com.ratelimiter.Tier;

import java.time.Instant;

/** Dependency-free regression tests; run with: java -ea -cp out/classes RateLimiterSelfTest */
public final class RateLimiterSelfTest {
    private RateLimiterSelfTest() {
    }

    public static void main(String[] args) {
        rejectsAfterFreeBurstAndRefills();
        premiumHasItsOwnBucketAndHigherAllowance();
        weightedRequestsAreAtomic();
        bucketCapacityIsBoundedAndFailsClosed();
        System.out.println("RateLimiterSelfTest: all tests passed");
    }

    private static void rejectsAfterFreeBurstAndRefills() {
        var clock = new FakeClock();
        var limiter = new RateLimiter(new InMemoryRateLimitStore(), RateLimitPolicies.defaults(), clock, 100);
        for (int i = 0; i < 10; i++) {
            assert limiter.check("free-user", Tier.FREE).allowed();
        }
        assert !limiter.check("free-user", Tier.FREE).allowed();
        clock.advanceNanos(1_000_000_000L);
        assert limiter.check("free-user", Tier.FREE).allowed();
    }

    private static void premiumHasItsOwnBucketAndHigherAllowance() {
        var clock = new FakeClock();
        var limiter = new RateLimiter(new InMemoryRateLimitStore(), RateLimitPolicies.defaults(), clock, 100);
        for (int i = 0; i < 10; i++) {
            assert limiter.check("same-user", Tier.FREE).allowed();
        }
        assert !limiter.check("same-user", Tier.FREE).allowed();
        assert limiter.check("same-user", Tier.PREMIUM).allowed();
    }

    private static void weightedRequestsAreAtomic() {
        var clock = new FakeClock();
        var limiter = new RateLimiter(new InMemoryRateLimitStore(), RateLimitPolicies.defaults(), clock, 100);
        RateLimitDecision first = limiter.check("weighted", Tier.FREE, 5);
        RateLimitDecision second = limiter.check("weighted", Tier.FREE, 6);
        assert first.allowed();
        assert !second.allowed();
        assert second.remaining() == 5;
    }

    private static void bucketCapacityIsBoundedAndFailsClosed() {
        var clock = new FakeClock();
        var store = new InMemoryRateLimitStore(1, 60_000_000_000L);
        var limiter = new RateLimiter(store, RateLimitPolicies.defaults(), clock, 100);
        assert limiter.check("first", Tier.FREE).allowed();
        assert !limiter.check("second", Tier.FREE).allowed();
    }

    private static final class FakeClock implements TimeSource {
        private long nanos;
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public long nanoTime() {
            return nanos;
        }

        @Override
        public Instant now() {
            return instant;
        }

        void advanceNanos(long amount) {
            nanos += amount;
            instant = instant.plusNanos(amount);
        }
    }
}
