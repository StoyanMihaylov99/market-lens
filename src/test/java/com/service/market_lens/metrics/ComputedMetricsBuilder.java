package com.service.market_lens.metrics;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Builds {@link ComputedMetrics} for scoring and API tests: everything {@code null} except ticker and price, plus
 * whatever the test sets. Public so other features' tests can use it.
 */
public final class ComputedMetricsBuilder {

    private @Nullable BigDecimal netIncomeTtm;
    private @Nullable BigDecimal netMargin;
    private @Nullable BigDecimal priceToEarnings;
    private @Nullable BigDecimal debtToEquity;
    private @Nullable BigDecimal freeCashFlowTtm;
    private @Nullable BigDecimal freeCashFlowYield;

    public static ComputedMetricsBuilder metrics() {
        return new ComputedMetricsBuilder();
    }

    /** Values every rule can score (P/E 20, D/E 0.8, FCF yield 4%, net margin 15%): all rules apply. */
    public static ComputedMetricsBuilder complete() {
        return metrics().netIncomeTtm("1000").priceToEarnings("20").debtToEquity("0.8")
                .freeCashFlowTtm("500").freeCashFlowYield("0.04").netMargin("0.15");
    }

    private static @Nullable BigDecimal d(@Nullable String value) {
        return value == null ? null : new BigDecimal(value);
    }

    public ComputedMetricsBuilder netIncomeTtm(@Nullable String value) { netIncomeTtm = d(value); return this; }
    public ComputedMetricsBuilder netMargin(@Nullable String value) { netMargin = d(value); return this; }
    public ComputedMetricsBuilder priceToEarnings(@Nullable String value) { priceToEarnings = d(value); return this; }
    public ComputedMetricsBuilder debtToEquity(@Nullable String value) { debtToEquity = d(value); return this; }
    public ComputedMetricsBuilder freeCashFlowTtm(@Nullable String value) { freeCashFlowTtm = d(value); return this; }
    public ComputedMetricsBuilder freeCashFlowYield(@Nullable String value) { freeCashFlowYield = d(value); return this; }

    public ComputedMetrics build() {
        return new ComputedMetrics("TEST", "USD", null, new BigDecimal("100"), null,
                null, null, null, netIncomeTtm, null, netMargin, null,
                priceToEarnings, null, null, null, null,
                null, null, debtToEquity,
                freeCashFlowTtm, null, freeCashFlowYield);
    }
}
