package com.service.market_lens.marketdata;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Current state of a market index (e.g. {@code ^GSPC}) plus recent closes for trend and volatility calculations.
 *
 * @param changePercent        today's change as a <b>decimal fraction</b> ({@code 0.0123} = +1.23%)
 * @param fiftyDayAverage      50-day moving average of the close
 * @param twoHundredDayAverage 200-day moving average of the close
 * @param history              recent daily closes, <b>newest first</b>; may be empty
 */
public record IndexSnapshot(
        String symbol,
        @Nullable String name,
        BigDecimal price,
        @Nullable BigDecimal changePercent,
        @Nullable BigDecimal fiftyDayAverage,
        @Nullable BigDecimal twoHundredDayAverage,
        List<DailyClose> history) {

    public IndexSnapshot {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(price, "price");
        history = List.copyOf(Objects.requireNonNull(history, "history"));
    }
}
