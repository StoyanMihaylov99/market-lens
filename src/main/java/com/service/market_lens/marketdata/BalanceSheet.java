package com.service.market_lens.marketdata;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Balance sheet at the end of one fiscal period. Net debt and D/E are computed by the metrics feature.
 *
 * @param currency    ISO 4217 code the figures are reported in
 * @param totalDebt   short-term + long-term debt
 * @param totalEquity total shareholders' equity (may be negative after large buybacks)
 */
public record BalanceSheet(
        LocalDate periodEnd,
        int fiscalYear,
        FiscalPeriod period,
        @Nullable String currency,
        @Nullable BigDecimal cashAndEquivalents,
        @Nullable BigDecimal totalDebt,
        @Nullable BigDecimal totalEquity,
        @Nullable BigDecimal totalAssets,
        @Nullable BigDecimal totalLiabilities) {

    public BalanceSheet {
        Objects.requireNonNull(periodEnd, "periodEnd");
        Objects.requireNonNull(period, "period");
    }
}
