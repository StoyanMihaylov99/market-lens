package com.service.market_lens.analysis.scoring;

import com.service.market_lens.metrics.ComputedMetrics;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * One deterministic rule of the score. Implementations read finished metrics only (never raw statements) and
 * return a skipped result instead of guessing when their inputs are missing. Each result carries the rule's stable id
 * (e.g. {@code valuation}), which matches its config key under {@code marketlens.scoring}.
 */
interface ScoringRule {

    RuleResult evaluate(ComputedMetrics metrics);

    /** Plain number for rationales: {@code 38.6500} → {@code "38.65"}. */
    static String number(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** Fraction as a percentage for rationales: {@code 0.276} → {@code "27.60%"}. Display only. */
    static String percent(BigDecimal fraction) {
        return fraction.movePointRight(2).setScale(2, RoundingMode.HALF_EVEN).toPlainString() + "%";
    }
}
