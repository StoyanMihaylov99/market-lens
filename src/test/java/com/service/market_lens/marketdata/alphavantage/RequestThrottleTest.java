package com.service.market_lens.marketdata.alphavantage;

import com.service.market_lens.marketdata.ProviderUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fake clock + recording sleeper: deterministic, no real waiting. */
class RequestThrottleTest {

    private long nowNanos = 1_000_000_000L;
    private final List<Duration> sleeps = new ArrayList<>();

    private RequestThrottle throttle(Duration interval) {
        return new RequestThrottle(interval, () -> nowNanos, d -> {
            sleeps.add(d);
            nowNanos += d.toNanos();   // sleeping advances the fake clock
        });
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void firstRequestNeverWaits() {
        throttle(Duration.ofSeconds(1)).acquire();

        assertThat(sleeps).isEmpty();
    }

    @Test
    void backToBackRequestsAreSpacedByInterval() {
        var throttle = throttle(Duration.ofSeconds(1));

        throttle.acquire();
        throttle.acquire();
        throttle.acquire();

        assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    @Test
    void waitsOnlyForTheRemainder() {
        var throttle = throttle(Duration.ofSeconds(1));

        throttle.acquire();
        nowNanos += Duration.ofMillis(300).toNanos();
        throttle.acquire();

        assertThat(sleeps).containsExactly(Duration.ofMillis(700));
    }

    @Test
    void noWaitWhenEnoughTimePassed() {
        var throttle = throttle(Duration.ofSeconds(1));

        throttle.acquire();
        nowNanos += Duration.ofSeconds(5).toNanos();
        throttle.acquire();

        assertThat(sleeps).isEmpty();
    }

    @Test
    void zeroIntervalDisablesThrottling() {
        var throttle = throttle(Duration.ZERO);

        throttle.acquire();
        throttle.acquire();

        assertThat(sleeps).isEmpty();
    }

    @Test
    void interruptBecomesProviderUnavailableAndKeepsFlag() {
        var throttle = new RequestThrottle(Duration.ofSeconds(1), () -> nowNanos, d -> {
            throw new InterruptedException();
        });
        throttle.acquire();

        assertThatThrownBy(throttle::acquire).isInstanceOf(ProviderUnavailableException.class);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void negativeIntervalIsRejected() {
        assertThatThrownBy(() -> new RequestThrottle(Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
