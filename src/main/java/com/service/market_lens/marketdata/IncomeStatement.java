package com.service.market_lens.marketdata;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Income statement for one fiscal period. Raw reported line items only; margins and EBITDA are computed
 * by the metrics feature.
 *
 * @param currency                    ISO 4217 code the figures are reported in
 * @param depreciationAndAmortization non-cash expense, needed to compute EBITDA
 * @param epsDiluted                  diluted earnings per share
 * @param weightedAvgSharesDiluted    diluted weighted-average share count for the period
 */
public record IncomeStatement(
        LocalDate periodEnd,
        int fiscalYear,
        FiscalPeriod period,
        @Nullable String currency,
        @Nullable BigDecimal revenue,
        @Nullable BigDecimal grossProfit,
        @Nullable BigDecimal operatingIncome,
        @Nullable BigDecimal netIncome,
        @Nullable BigDecimal depreciationAndAmortization,
        @Nullable BigDecimal epsDiluted,
        @Nullable Long weightedAvgSharesDiluted) {

    public IncomeStatement {
        Objects.requireNonNull(periodEnd, "periodEnd");
        Objects.requireNonNull(period, "period");
    }
}
