package com.service.market_lens.analysis.scoring;

import com.service.market_lens.metrics.ComputedMetrics;

import java.math.BigDecimal;

import static com.service.market_lens.analysis.scoring.ScoringRule.percent;

/**
 * Positive free cash flow is required for any points; a higher FCF yield scores higher. Non-positive FCF earns
 * nothing even without a yield (losses are never rewarded by skipping); positive FCF without a yield (no market cap,
 * or price and statements in different currencies) cannot be graded and is skipped.
 */
final class CashFlowRule implements ScoringRule {

    static final String NAME = "cash-flow";

    private final ScoringProperties.CashFlow config;

    CashFlowRule(ScoringProperties.CashFlow config) {
        this.config = config;
    }

    @Override
    public RuleResult evaluate(ComputedMetrics metrics) {
        int max = config.maxPoints();
        BigDecimal fcf = metrics.freeCashFlowTtm();
        if (fcf == null) {
            return RuleResult.skipped(NAME, max, "TTM free cash flow unavailable");
        }
        if (fcf.signum() <= 0) {
            return RuleResult.applied(NAME, Tier.POOR, max, "TTM free cash flow is not positive");
        }
        BigDecimal yield = metrics.freeCashFlowYield();
        if (yield == null) {
            return RuleResult.skipped(NAME, max, "FCF yield unavailable (no market cap, or currencies differ)");
        }
        String value = "FCF yield " + percent(yield);
        if (yield.compareTo(config.highYield()) >= 0) {
            return RuleResult.applied(NAME, Tier.STRONG, max, value + " is at or above " + percent(config.highYield()));
        }
        if (yield.compareTo(config.moderateYield()) >= 0) {
            return RuleResult.applied(NAME, Tier.GOOD, max, value + " is at or above " + percent(config.moderateYield()));
        }
        return RuleResult.applied(NAME, Tier.WEAK, max, value + " is positive but below " + percent(config.moderateYield()));
    }
}
