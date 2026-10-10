package com.ratelimiter;

import java.time.Instant;

/** Time abstraction used by the limiter; monotonic time controls refill. */
public interface TimeSource {
    long nanoTime();

    Instant now();

    TimeSource SYSTEM = new TimeSource() {
        @Override
        public long nanoTime() {
            return System.nanoTime();
        }

        @Override
        public Instant now() {
            return Instant.now();
        }
    };
}
