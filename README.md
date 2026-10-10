# Tiered rate limiter

Dependency-free Java 19 token-bucket limiter for `FREE` and `PREMIUM` customers.

The default policies are:

- Free: 60 requests/minute, with a burst of 10.
- Premium: 600 requests/minute, with a burst of 100.

The sustained rate refills continuously, so requests do not all become available
at a fixed window boundary. Each check is atomic and returns HTTP-compatible
`RateLimit-*` headers plus `Retry-After` when rejected.

## Usage

```java
var store = new InMemoryRateLimitStore();
var limiter = new RateLimiter(store, RateLimitPolicies.defaults());

RateLimitDecision decision = limiter.check("account:123", Tier.PREMIUM);
if (!decision.allowed()) {
    // Return HTTP 429 and include decision.headers() in the response.
}
```

Use a stable authenticated account ID as the key. Do not use an untrusted raw
header without authentication. For endpoint-specific quotas, namespace the key,
for example `account:123:search`. Free and premium are deliberately separate
bucket namespaces; the tier must come from trusted entitlement data, not from a
client-provided value.

## Running

```text
javac -d out/classes (Get-ChildItem -Recurse src -Filter *.java).FullName
java -cp out/classes Main
```

The regression tests can be run without a test framework:

```text
javac -d out/classes (Get-ChildItem -Recurse src,test -Filter *.java).FullName
java -ea -cp out/classes RateLimiterSelfTest
```

`InMemoryRateLimitStore` is bounded and thread-safe, but its state is local to
one JVM. In a multi-instance deployment, implement `RateLimitStore` with a
shared datastore and make refill+consume one atomic operation (Redis Lua is a
common choice). Do not put a read/write round trip behind this interface.

When local bucket capacity is exhausted, the limiter fails closed for new keys.
Configure the capacity above expected active principals and call
`InMemoryRateLimitStore.cleanup(System.nanoTime())` periodically for sparse
traffic.
