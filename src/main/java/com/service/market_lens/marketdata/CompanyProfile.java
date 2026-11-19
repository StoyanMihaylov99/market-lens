package com.service.market_lens.marketdata;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Static description of a listed company.
 *
 * @param currency ISO 4217 code the company reports in (e.g. {@code USD}); may differ from the trading currency for ADRs
 * @param country  ISO 3166 country code of the company's domicile
 */
public record CompanyProfile(
        String ticker,
        @Nullable String name,
        @Nullable String exchange,
        @Nullable String currency,
        @Nullable String sector,
        @Nullable String industry,
        @Nullable String country) {

    public CompanyProfile {
        Objects.requireNonNull(ticker, "ticker");
    }
}
