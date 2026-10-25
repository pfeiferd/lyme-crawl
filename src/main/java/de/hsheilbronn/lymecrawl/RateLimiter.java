package de.hsheilbronn.lymecrawl;

import java.time.Duration;

/** Enforces a minimum interval between successive calls to {@link #await()}. */
public class RateLimiter {

    private final long minIntervalNanos;
    private long last;
    private boolean used;

    public RateLimiter(Duration minInterval) {
        this.minIntervalNanos = minInterval.toNanos();
    }

    public synchronized void await() throws InterruptedException {
        if (used) {
            long wait = last + minIntervalNanos - System.nanoTime();
            if (wait > 0) Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
        }
        used = true;
        last = System.nanoTime();
    }
}
