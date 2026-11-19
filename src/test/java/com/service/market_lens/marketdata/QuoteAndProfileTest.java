package com.service.market_lens.marketdata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class QuoteAndProfileTest {

    @Test
    void quoteRequiresTickerPriceAndTimestamp() {
        var price = new BigDecimal("230.50");
        var asOf = Instant.parse("2026-11-19T10:00:00Z");

        assertThatNullPointerException().isThrownBy(() -> new Quote(null, price, null, null, asOf))
                .withMessageContaining("ticker");
        assertThatNullPointerException().isThrownBy(() -> new Quote("AAPL", null, null, null, asOf))
                .withMessageContaining("price");
        assertThatNullPointerException().isThrownBy(() -> new Quote("AAPL", price, null, null, null))
                .withMessageContaining("asOf");
    }

    @Test
    void profileRequiresTicker() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CompanyProfile(null, null, null, null, null, null, null))
                .withMessageContaining("ticker");
    }
}
