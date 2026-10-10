import com.ratelimiter.InMemoryRateLimitStore;
import com.ratelimiter.RateLimitDecision;
import com.ratelimiter.RateLimiter;
import com.ratelimiter.RateLimitPolicies;
import com.ratelimiter.Tier;

/** Small executable example. The library classes are under {@code com.ratelimiter}. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        var store = new InMemoryRateLimitStore();
        var limiter = new RateLimiter(store, RateLimitPolicies.defaults());

        for (int request = 1; request <= 12; request++) {
            RateLimitDecision decision = limiter.check("user-42", Tier.FREE);
            System.out.printf("request=%d allowed=%s remaining=%d headers=%s%n",
                    request, decision.allowed(), decision.remaining(), decision.headers());
        }
    }
}
