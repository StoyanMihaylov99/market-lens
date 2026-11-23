package com.service.market_lens.marketdata;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataExceptionsTest {

    @Test
    void tickerNotFoundCarriesTicker() {
        var e = new TickerNotFoundException("ZZZZ");

        assertThat(e.ticker()).isEqualTo("ZZZZ");
        assertThat(e).hasMessage("Ticker not found: ZZZZ");
    }

    @Test
    void providerUnavailableKeepsCause() {
        var cause = new IOException("connect timed out");
        var e = new ProviderUnavailableException("alphavantage", "request timed out", cause);

        assertThat(e.provider()).isEqualTo("alphavantage");
        assertThat(e).hasMessage("Market data provider 'alphavantage' is unavailable: request timed out")
                .hasCause(cause);
    }

    @Test
    void rateLimitedExposesRetryAfterWhenKnown() {
        var e = new ProviderRateLimitedException("alphavantage", Duration.ofSeconds(30));

        assertThat(e.provider()).isEqualTo("alphavantage");
        assertThat(e.retryAfter()).contains(Duration.ofSeconds(30));
        assertThat(e).hasMessage("Market data provider 'alphavantage' rate limit exceeded; retry after PT30S");
    }

    @Test
    void rateLimitedWithoutRetryAfter() {
        var e = new ProviderRateLimitedException("alphavantage", null);

        assertThat(e.retryAfter()).isEmpty();
        assertThat(e).hasMessage("Market data provider 'alphavantage' rate limit exceeded");
    }

    @Test
    void hierarchyIsClosedSoSwitchIsExhaustive() {
        // Compiles without a `default` branch only because MarketDataException is sealed.
        // Adding a fourth subclass makes this (and every similar switch) fail to compile until it is handled.
        MarketDataException e = new TickerNotFoundException("ZZZZ");

        int status = switch (e) {
            case TickerNotFoundException notFound -> 404;
            case ProviderUnavailableException unavailable -> 503;
            case ProviderRateLimitedException rateLimited -> 503;
        };

        assertThat(status).isEqualTo(404);
    }
}
