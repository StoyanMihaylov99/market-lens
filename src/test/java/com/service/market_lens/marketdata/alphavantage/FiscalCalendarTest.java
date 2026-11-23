package com.service.market_lens.marketdata.alphavantage;

import com.service.market_lens.marketdata.FiscalPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;

class FiscalCalendarTest {

    private final FiscalCalendar apple = new FiscalCalendar(Month.SEPTEMBER);

    @ParameterizedTest(name = "Apple {0} → {1} FY{2}")
    @CsvSource({
            "2025-12-31, Q1, 2026",
            "2026-03-31, Q2, 2026",
            "2026-06-30, Q3, 2026",
            "2026-09-30, Q4, 2026",
            "2026-12-31, Q1, 2027"})
    void septemberYearEnd(LocalDate periodEnd, FiscalPeriod quarter, int fiscalYear) {
        assertThat(apple.quarter(periodEnd)).isEqualTo(quarter);
        assertThat(apple.fiscalYear(periodEnd)).isEqualTo(fiscalYear);
    }

    @ParameterizedTest(name = "calendar year {0} → {1}")
    @CsvSource({"2026-03-31, Q1", "2026-06-30, Q2", "2026-09-30, Q3", "2026-12-31, Q4"})
    void calendarYear(LocalDate periodEnd, FiscalPeriod quarter) {
        assertThat(FiscalCalendar.CALENDAR_YEAR.quarter(periodEnd)).isEqualTo(quarter);
        assertThat(FiscalCalendar.CALENDAR_YEAR.fiscalYear(periodEnd)).isEqualTo(2026);
    }

    @ParameterizedTest(name = "52/53-week year ending 1 Oct: {0} → {1}")
    @CsvSource({"2025-12-31, Q1", "2026-04-01, Q2", "2026-07-01, Q3", "2026-09-30, Q4"})
    void toleratesQuartersEndingNearMonthBoundaries(LocalDate periodEnd, FiscalPeriod quarter) {
        var calendar = FiscalCalendar.fromLatestAnnualEnd(LocalDate.of(2025, 10, 1));
        assertThat(calendar.quarter(periodEnd)).isEqualTo(quarter);
    }

    @Test
    void fallsBackToCalendarYearWithoutAnnualReports() {
        assertThat(FiscalCalendar.fromLatestAnnualEnd(null)).isEqualTo(FiscalCalendar.CALENDAR_YEAR);
    }
}
