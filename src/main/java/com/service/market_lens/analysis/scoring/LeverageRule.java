package com.service.market_lens.analysis.scoring;

import com.service.market_lens.metrics.ComputedMetrics;

import java.math.BigDecimal;

import static com.service.market_lens.analysis.scoring.ScoringRule.number;

/**
 * Lower debt-to-equity scores higher. Skipped when D/E is unavailable, which includes zero or negative equity:
 * negative equity is often the result of buybacks rather than distress, so it is not graded either way.
 */
final class LeverageRule implements ScoringRule {

    static final String NAME = "leverage";

    private final ScoringProperties.Leverage config;

    LeverageRule(ScoringProperties.Leverage config) {
        this.config = config;
    }

    @Override
    public RuleResult evaluate(ComputedMetrics metrics) {
        int max = config.maxPoints();
        BigDecimal de = metrics.debtToEquity();
        if (de == null) {
            return RuleResult.skipped(NAME, max, "Debt-to-equity unavailable (missing data or equity not positive)");
        }
        String value = "Debt-to-equity " + number(de);
        if (de.compareTo(config.low()) <= 0) {
            return RuleResult.applied(NAME, Tier.STRONG, max, value + " is at or below " + number(config.low()));
        }
        if (de.compareTo(config.moderate()) <= 0) {
            return RuleResult.applied(NAME, Tier.GOOD, max, value + " is at or below " + number(config.moderate()));
        }
        if (de.compareTo(config.high()) <= 0) {
            return RuleResult.applied(NAME, Tier.WEAK, max, value + " is at or below " + number(config.high()));
        }
        return RuleResult.applied(NAME, Tier.POOR, max, value + " is above " + number(config.high()));
    }
}
