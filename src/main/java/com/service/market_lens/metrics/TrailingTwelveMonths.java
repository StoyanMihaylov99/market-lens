package com.service.market_lens.metrics;

import com.service.market_lens.marketdata.FiscalPeriod;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Function;

/**
 * Trailing-twelve-month (TTM) sums: the four newest quarters added up.
 * <p>
 * A TTM figure is only trustworthy when the four quarters are really one year. The sum is {@code null} when
 * <ul>
 *   <li>fewer than four statements exist,</li>
 *   <li>any of the four is not a quarter ({@link FiscalPeriod#FY}),</li>
 *   <li>two neighbouring period ends are not one quarter apart ({@link #MIN_GAP_DAYS}–{@link #MAX_GAP_DAYS} days):
 *       a missing quarter would stretch the "year" over more than twelve months, a duplicated one would count a
 *       quarter twice, or</li>
 *   <li>the value is missing in any of the four quarters (a partial sum would understate the year).</li>
 * </ul>
 */
final class TrailingTwelveMonths {

    static final int QUARTERS = 4;

    /**
     * Calendar quarters are 89–92 days apart; 52/53-week fiscal years have 91-day quarters and a 98-day one in
     * a 53-week year. A missing quarter makes a gap of about 180 days, a duplicated one a gap of 0.
     */
    static final long MIN_GAP_DAYS = 80;
    static final long MAX_GAP_DAYS = 105;

    private TrailingTwelveMonths() {
    }

    /**
     * @param statements newest first, as delivered by the market data port
     * @param periodEnd  the statement's period end
     * @param period     the statement's fiscal period
     * @param value      the line item to sum
     */
    static <T> @Nullable BigDecimal sum(List<T> statements,
                                        Function<T, LocalDate> periodEnd,
                                        Function<T, FiscalPeriod> period,
                                        Function<T, @Nullable BigDecimal> value) {
        if (!coversOneYear(statements, periodEnd, period)) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (T statement : statements.subList(0, QUARTERS)) {
            BigDecimal v = value.apply(statement);
            if (v == null) {
                return null;
            }
            total = total.add(v);
        }
        return total;
    }

    static <T> boolean coversOneYear(List<T> statements,
                                     Function<T, LocalDate> periodEnd,
                                     Function<T, FiscalPeriod> period) {
        if (statements.size() < QUARTERS) {
            return false;
        }
        List<T> year = statements.subList(0, QUARTERS);
        if (year.stream().map(period).anyMatch(p -> p == FiscalPeriod.FY)) {
            return false;
        }
        for (int i = 1; i < QUARTERS; i++) {
            long gap = ChronoUnit.DAYS.between(periodEnd.apply(year.get(i)), periodEnd.apply(year.get(i - 1)));
            if (gap < MIN_GAP_DAYS || gap > MAX_GAP_DAYS) {
                return false;
            }
        }
        return true;
    }
}
