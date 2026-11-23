package com.service.market_lens.marketdata.alphavantage;

import com.service.market_lens.marketdata.FiscalPeriod;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.time.Month;
import java.util.Objects;

/**
 * Derives fiscal year and quarter from a period-end date. Alpha Vantage only sends {@code fiscalDateEnding}, but
 * its annual reports tell us in which month the company's fiscal year ends (September for Apple, December for most).
 * <p>
 * Conventions:
 * <ul>
 *   <li>The fiscal year is named after the calendar year in which it <b>ends</b>
 *       (Apple's quarter ending 2025-12-31 is Q1 of FY2026).</li>
 *   <li>Quarters are counted in months after the fiscal year end: 1–3 → Q1, 4–6 → Q2, 7–9 → Q3, 10–12 → Q4.
 *       Rounding by whole quarters tolerates 52/53-week years whose quarters end a few days off a month end.</li>
 * </ul>
 */
record FiscalCalendar(Month yearEndMonth) {

    static final FiscalCalendar CALENDAR_YEAR = new FiscalCalendar(Month.DECEMBER);

    FiscalCalendar {
        Objects.requireNonNull(yearEndMonth, "yearEndMonth");
    }

    /** Uses the newest annual period end; falls back to a calendar year when there are no annual reports. */
    static FiscalCalendar fromLatestAnnualEnd(@Nullable LocalDate latestAnnualPeriodEnd) {
        return latestAnnualPeriodEnd == null ? CALENDAR_YEAR : new FiscalCalendar(latestAnnualPeriodEnd.getMonth());
    }

    int fiscalYear(LocalDate periodEnd) {
        return periodEnd.getMonthValue() <= yearEndMonth.getValue() ? periodEnd.getYear() : periodEnd.getYear() + 1;
    }

    FiscalPeriod quarter(LocalDate periodEnd) {
        int monthsAfterYearEnd = Math.floorMod(periodEnd.getMonthValue() - yearEndMonth.getValue(), 12);
        return switch (monthsAfterYearEnd) {
            case 1, 2, 3 -> FiscalPeriod.Q1;
            case 4, 5, 6 -> FiscalPeriod.Q2;
            case 7, 8, 9 -> FiscalPeriod.Q3;
            default -> FiscalPeriod.Q4; // 10, 11 and 0 (the year-end month itself)
        };
    }
}
