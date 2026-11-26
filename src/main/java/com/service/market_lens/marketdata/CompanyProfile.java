package com.service.market_lens.marketdata;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Static description of a listed company.
 *
 * @param currency ISO 4217 code the stock <b>trades</b> in, i.e. the currency of {@link Quote#price()} (e.g. {@code USD}).
 *                 For ADRs it may differ from the statements' reporting currency; the metrics feature relies on this to
 *                 avoid mixing currencies in valuation ratios
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
