package com.service.market_lens.marketdata;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Latest market price of a company's stock.
 *
 * @param price             last traded price in the trading currency
 * @param marketCap         market capitalisation as reported by the vendor (accounts for share classes)
 * @param sharesOutstanding total shares outstanding
 * @param asOf              when the price was observed (vendor timestamp)
 */
public record Quote(
        String ticker,
        BigDecimal price,
        @Nullable BigDecimal marketCap,
        @Nullable Long sharesOutstanding,
        Instant asOf) {

    public Quote {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(asOf, "asOf");
    }
}
