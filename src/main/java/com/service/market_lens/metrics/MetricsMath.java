package com.service.market_lens.metrics;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * The single place for rounding rules in the metrics feature.
 * <ul>
 *   <li>Money (sums of reported figures, EV, net debt, FCF) is <b>exact</b>: never rounded.</li>
 *   <li>Every quotient (ratios, margins, yields, per-share values) is computed with {@link #CONTEXT} and then
 *       rounded to {@link #SCALE} decimal places, {@link RoundingMode#HALF_EVEN}. Fractions keep the project's unit
 *       ({@code 0.2760} = 27.60%), so 4 places means a precision of 0.01 percentage points.</li>
 *   <li>A missing operand or a zero denominator yields {@code null}, never an exception. Sign rules
 *       ("P/E only for positive earnings") are applied by the caller, because they differ per metric.</li>
 * </ul>
 */
final class MetricsMath {

    /** Precision for intermediate division: 16 significant digits. */
    static final MathContext CONTEXT = MathContext.DECIMAL64;

    /** Decimal places of every computed quotient. */
    static final int SCALE = 4;

    static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

    private MetricsMath() {
    }

    /** {@code numerator / denominator} rounded to {@link #SCALE}; {@code null} if an operand is missing or the denominator is zero. */
    static @Nullable BigDecimal divide(@Nullable BigDecimal numerator, @Nullable BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, CONTEXT).setScale(SCALE, ROUNDING);
    }

    /** Like {@link #divide} but only for a strictly positive denominator (e.g. P/E needs positive earnings). */
    static @Nullable BigDecimal divideByPositive(@Nullable BigDecimal numerator, @Nullable BigDecimal denominator) {
        return isPositive(denominator) ? divide(numerator, denominator) : null;
    }

    /** {@code a + b}, or {@code null} if either is missing. */
    static @Nullable BigDecimal add(@Nullable BigDecimal a, @Nullable BigDecimal b) {
        return a == null || b == null ? null : a.add(b);
    }

    /** {@code a - b}, or {@code null} if either is missing. */
    static @Nullable BigDecimal subtract(@Nullable BigDecimal a, @Nullable BigDecimal b) {
        return a == null || b == null ? null : a.subtract(b);
    }

    static boolean isPositive(@Nullable BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
