package com.service.market_lens.marketdata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** Closing price of an instrument on one trading day. */
public record DailyClose(LocalDate date, BigDecimal close) {

    public DailyClose {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(close, "close");
    }
}
