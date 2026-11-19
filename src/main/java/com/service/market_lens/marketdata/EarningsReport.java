package com.service.market_lens.marketdata;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One quarterly earnings release: actual results versus analyst estimates.
 * <p>
 * Estimates are {@code null} when no analyst covers the company. Period details and net income are
 * {@code null} when the vendor's earnings endpoint does not provide them.
 *
 * @param reportDate the day the results were announced
 * @param periodEnd  the last day of the fiscal period the results cover
 */
public record EarningsReport(
        String ticker,
        LocalDate reportDate,
        @Nullable LocalDate periodEnd,
        @Nullable Integer fiscalYear,
        @Nullable FiscalPeriod period,
        @Nullable BigDecimal epsActual,
        @Nullable BigDecimal epsEstimate,
        @Nullable BigDecimal revenueActual,
        @Nullable BigDecimal revenueEstimate,
        @Nullable BigDecimal netIncome) {

    public EarningsReport {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(reportDate, "reportDate");
    }
}
