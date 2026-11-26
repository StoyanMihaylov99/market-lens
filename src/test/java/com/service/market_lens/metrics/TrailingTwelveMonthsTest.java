package com.service.market_lens.metrics;

import com.service.market_lens.marketdata.FiscalPeriod;
import com.service.market_lens.marketdata.IncomeStatement;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrailingTwelveMonthsTest {

    private static IncomeStatement quarter(String periodEnd, FiscalPeriod period, @Nullable String revenue) {
        return new IncomeStatement(LocalDate.parse(periodEnd), 2026, period, "USD",
                revenue == null ? null : new BigDecimal(revenue), null, null, null, null, null, null);
    }

    private static @Nullable BigDecimal revenueTtm(List<IncomeStatement> statements) {
        return TrailingTwelveMonths.sum(statements, IncomeStatement::periodEnd, IncomeStatement::period,
                IncomeStatement::revenue);
    }

    private static List<IncomeStatement> year() {
        return new ArrayList<>(List.of(
                quarter("2026-09-30", FiscalPeriod.Q3, "4"),
                quarter("2026-06-30", FiscalPeriod.Q2, "3"),
                quarter("2026-03-31", FiscalPeriod.Q1, "2"),
                quarter("2025-12-31", FiscalPeriod.Q4, "1")));
    }

    @Test
    void sumsTheFourNewestQuarters() {
        List<IncomeStatement> statements = year();
        statements.add(quarter("2025-09-30", FiscalPeriod.Q3, "1000")); // fifth quarter is ignored
        assertThat(revenueTtm(statements)).isEqualByComparingTo("10");
    }

    @Test
    void fewerThanFourQuartersIsNotAYear() {
        assertThat(revenueTtm(year().subList(0, 3))).isNull();
        assertThat(revenueTtm(List.of())).isNull();
    }

    @Test
    void aMissingValueInAnyQuarterMakesTheSumUnknown() {
        List<IncomeStatement> statements = year();
        statements.set(2, quarter("2026-03-31", FiscalPeriod.Q1, null));
        assertThat(revenueTtm(statements)).isNull();
    }

    @Test
    void aGapBetweenQuartersIsNotAYear() {
        // 2026-03-31 missing: the four newest now span Sep 2026 back to Sep 2025 (365 days)
        List<IncomeStatement> statements = List.of(
                quarter("2026-09-30", FiscalPeriod.Q3, "4"),
                quarter("2026-06-30", FiscalPeriod.Q2, "3"),
                quarter("2025-12-31", FiscalPeriod.Q4, "1"),
                quarter("2025-09-30", FiscalPeriod.Q3, "1"));
        assertThat(revenueTtm(statements)).isNull();
    }

    @Test
    void fiftyTwoFiftyThreeWeekYearsStillCount() {
        // Apple-style fiscal quarters end on the last Saturday: span 280 days instead of ~273
        List<IncomeStatement> statements = List.of(
                quarter("2026-09-26", FiscalPeriod.Q4, "4"),
                quarter("2026-06-27", FiscalPeriod.Q3, "3"),
                quarter("2026-03-28", FiscalPeriod.Q2, "2"),
                quarter("2025-12-20", FiscalPeriod.Q1, "1"));
        assertThat(revenueTtm(statements)).isEqualByComparingTo("10");
    }

    @Test
    void anAnnualStatementIsNotAQuarter() {
        List<IncomeStatement> statements = year();
        statements.set(3, quarter("2025-12-31", FiscalPeriod.FY, "1"));
        assertThat(revenueTtm(statements)).isNull();
    }

    @Test
    void aDuplicatedQuarterIsNotCountedTwice() {
        // span newest → oldest is 183 days, which a span-only check would accept
        List<IncomeStatement> statements = List.of(
                quarter("2026-09-30", FiscalPeriod.Q3, "4"),
                quarter("2026-09-30", FiscalPeriod.Q3, "4"),
                quarter("2026-06-30", FiscalPeriod.Q2, "3"),
                quarter("2026-03-31", FiscalPeriod.Q1, "2"));
        assertThat(revenueTtm(statements)).isNull();
    }

    @Test
    void aFiftyThreeWeekYearsFourteenWeekQuarterCounts() {
        // 98 days between 2026-01-03 and 2026-04-11
        List<IncomeStatement> statements = List.of(
                quarter("2026-10-03", FiscalPeriod.Q4, "4"),
                quarter("2026-07-04", FiscalPeriod.Q3, "3"),
                quarter("2026-04-11", FiscalPeriod.Q2, "2"),
                quarter("2026-01-03", FiscalPeriod.Q1, "1"));
        assertThat(revenueTtm(statements)).isEqualByComparingTo("10");
    }

    @Test
    void duplicatePeriodEndsAreNotAYear() {
        List<IncomeStatement> statements = List.of(
                quarter("2026-09-30", FiscalPeriod.Q3, "1"),
                quarter("2026-09-30", FiscalPeriod.Q3, "1"),
                quarter("2026-09-30", FiscalPeriod.Q3, "1"),
                quarter("2026-09-30", FiscalPeriod.Q3, "1"));
        assertThat(revenueTtm(statements)).isNull();
    }
}
