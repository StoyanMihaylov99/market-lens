package com.service.market_lens.marketdata;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Cash flow statement for one fiscal period. Free cash flow is computed by the metrics feature from these inputs.
 *
 * @param currency           ISO 4217 code the figures are reported in
 * @param capitalExpenditure capex as reported, i.e. normally a <b>negative</b> number (cash going out)
 */
public record CashFlowStatement(
        LocalDate periodEnd,
        int fiscalYear,
        FiscalPeriod period,
        @Nullable String currency,
        @Nullable BigDecimal operatingCashFlow,
        @Nullable BigDecimal capitalExpenditure) {

    public CashFlowStatement {
        Objects.requireNonNull(periodEnd, "periodEnd");
        Objects.requireNonNull(period, "period");
    }
}
