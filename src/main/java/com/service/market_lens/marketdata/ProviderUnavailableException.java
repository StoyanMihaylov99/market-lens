package com.service.market_lens.marketdata;

import org.jspecify.annotations.Nullable;

/**
 * The provider could not deliver data: server error (5xx), timeout, connection failure or an unreadable response.
 * Usually temporary, so a short retry may help. Maps to HTTP 503.
 */
public final class ProviderUnavailableException extends MarketDataException {

    private final String provider;

    /**
     * @param provider short provider name, e.g. {@code alphavantage}
     * @param reason   what went wrong, without request URLs or keys
     */
    public ProviderUnavailableException(String provider, String reason, @Nullable Throwable cause) {
        super("Market data provider '" + provider + "' is unavailable: " + reason, cause);
        this.provider = provider;
    }

    public String provider() {
        return provider;
    }
}
