package com.service.market_lens.marketdata;

import org.jspecify.annotations.Nullable;

/**
 * Base class for every failure a {@link MarketDataProvider} reports.
 * <p>
 * Unchecked: callers cannot recover from a vendor outage locally, so these propagate to a single
 * {@code @RestControllerAdvice} that maps them to HTTP responses. Sealed: the set of failures is closed,
 * so a {@code switch} over it is checked for exhaustiveness by the compiler.
 * <p>
 * Messages must never contain request URLs: vendors such as Alpha Vantage pass the API key as a query parameter.
 */
public abstract sealed class MarketDataException extends RuntimeException
        permits TickerNotFoundException, ProviderUnavailableException, ProviderRateLimitedException {

    protected MarketDataException(String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
