package com.service.market_lens.marketdata.alphavantage;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * Connection settings for the Alpha Vantage adapter ({@code marketlens.market-data.alphavantage.*}).
 * <p>
 * The API key is not here: it is vendor-neutral and lives in
 * {@link com.service.market_lens.config.MarketLensProperties.MarketData#apiKey()}.
 *
 * @param baseUrl            scheme + host only, e.g. {@code https://www.alphavantage.co}; tests and the mock profile
 *                           point it at WireMock
 * @param connectTimeout     max time to open the TCP connection
 * @param readTimeout        max time to wait for the response
 * @param minRequestInterval minimum gap between two requests (client-side throttle); {@code 0s} disables it
 */
@Validated
@ConfigurationProperties(prefix = "marketlens.market-data.alphavantage")
record AlphaVantageProperties(
        @NotNull URI baseUrl,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration minRequestInterval) {

    AlphaVantageProperties {
        if (minRequestInterval != null && minRequestInterval.isNegative()) {
            throw new IllegalArgumentException("min-request-interval must not be negative");
        }
    }
}
