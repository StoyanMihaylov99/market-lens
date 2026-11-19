package com.service.market_lens.marketdata;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** Income statement, balance sheet and cash flow share the same identity fields, so they are tested together. */
class FinancialStatementsTest {

    private static final LocalDate PERIOD_END = LocalDate.of(2026, 6, 27);

    @Test
    void allowsMissingLineItems() {
        var income = new IncomeStatement(PERIOD_END, 2026, FiscalPeriod.Q3, null,
                null, null, null, null, null, null, null);
        var balance = new BalanceSheet(PERIOD_END, 2026, FiscalPeriod.Q3, null, null, null, null, null, null);
        var cashFlow = new CashFlowStatement(PERIOD_END, 2026, FiscalPeriod.Q3, null, null, null);

        assertThat(income.revenue()).isNull();
        assertThat(balance.totalDebt()).isNull();
        assertThat(cashFlow.capitalExpenditure()).isNull();
    }

    @Test
    void rejectsMissingPeriodIdentity() {
        assertThatNullPointerException()
                .isThrownBy(() -> new IncomeStatement(null, 2026, FiscalPeriod.Q3, null,
                        null, null, null, null, null, null, null))
                .withMessageContaining("periodEnd");
        assertThatNullPointerException()
                .isThrownBy(() -> new BalanceSheet(PERIOD_END, 2026, null, null, null, null, null, null, null))
                .withMessageContaining("period");
        assertThatNullPointerException()
                .isThrownBy(() -> new CashFlowStatement(null, 2026, FiscalPeriod.Q3, null, null, null))
                .withMessageContaining("periodEnd");
    }
}
