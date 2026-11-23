package com.service.market_lens.marketdata.alphavantage;

import com.service.market_lens.marketdata.ProviderUnavailableException;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;

/**
 * Minimal client-side rate limiter: guarantees at least {@code minInterval} between the starts of two requests.
 * Callers <b>wait</b> for their slot instead of failing, so a burst of N calls takes about N × interval.
 * <p>
 * Why: Alpha Vantage's free tier rejects bursts (it asks for about 1 request per second) and answers them with an HTTP
 * 200 {@code Information} body that still counts against the 25/day quota. Spacing calls avoids wasting quota. The
 * daily cap itself is handled by caching (Phase 7), not here.
 * <p>
 * Uses a {@link ReentrantLock} rather than {@code synchronized}: sleeping inside {@code synchronized} pins the carrier
 * thread when the app runs on virtual threads (Java 21). Time source and sleep are injectable for deterministic tests.
 */
final class RequestThrottle {

    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    private final long minIntervalNanos;
    private final LongSupplier nanoTime;
    private final Sleeper sleeper;
    private final ReentrantLock lock = new ReentrantLock(true); // fair: callers get slots in arrival order

    private boolean started;
    private long nextAllowedNanos;

    RequestThrottle(Duration minInterval) {
        this(minInterval, System::nanoTime, d -> Thread.sleep(d));
    }

    RequestThrottle(Duration minInterval, LongSupplier nanoTime, Sleeper sleeper) {
        if (minInterval.isNegative()) {
            throw new IllegalArgumentException("minInterval must not be negative");
        }
        this.minIntervalNanos = minInterval.toNanos();
        this.nanoTime = Objects.requireNonNull(nanoTime);
        this.sleeper = Objects.requireNonNull(sleeper);
    }

    /** Blocks until this caller may send its request. */
    void acquire() {
        lock.lock();
        try {
            long now = nanoTime.getAsLong();
            // compare via subtraction: nanoTime values may overflow, differences don't
            if (started && nextAllowedNanos - now > 0) {
                sleeper.sleep(Duration.ofNanos(nextAllowedNanos - now));
                now = nextAllowedNanos;
            }
            started = true;
            nextAllowedNanos = now + minIntervalNanos;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // keep the interrupt visible to the caller's thread
            throw new ProviderUnavailableException(AlphaVantageConfiguration.PROVIDER,
                    "interrupted while waiting for a request slot", e);
        } finally {
            lock.unlock();
        }
    }
}
