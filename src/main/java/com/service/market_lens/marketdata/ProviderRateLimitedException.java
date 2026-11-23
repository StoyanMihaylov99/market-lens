package com.service.market_lens.marketdata;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Optional;

/**
 * The provider rejected the request because our quota is exhausted (HTTP 429 from the vendor, or our own
 * client-side limiter). Retrying immediately makes it worse; wait for {@link #retryAfter()} when known.
 * <p>
 * Maps to HTTP <b>503</b> with {@code Retry-After}, not 429: the upstream limited <i>us</i>, our client did nothing wrong.
 */
public final class ProviderRateLimitedException extends MarketDataException {

    private final String provider;
    private final @Nullable Duration retryAfter;

    /**
     * @param provider   short provider name, e.g. {@code alphavantage}
     * @param retryAfter how long to wait before retrying, if the vendor said so (e.g. a {@code Retry-After} header)
     */
    public ProviderRateLimitedException(String provider, @Nullable Duration retryAfter) {
        super("Market data provider '" + provider + "' rate limit exceeded"
                + (retryAfter != null ? "; retry after " + retryAfter : ""), null);
        this.provider = provider;
        this.retryAfter = retryAfter;
    }

    public String provider() {
        return provider;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
