package com.service.market_lens.analysis.scoring;

import com.service.market_lens.metrics.ComputedMetrics;

import java.math.BigDecimal;

import static com.service.market_lens.analysis.scoring.ScoringRule.percent;

/** Higher TTM net margin scores higher; a loss earns nothing. */
final class ProfitabilityRule implements ScoringRule {

    static final String NAME = "profitability";

    private final ScoringProperties.Profitability config;

    ProfitabilityRule(ScoringProperties.Profitability config) {
        this.config = config;
    }

    @Override
    public RuleResult evaluate(ComputedMetrics metrics) {
        int max = config.maxPoints();
        BigDecimal margin = metrics.netMargin();
        if (margin == null) {
            return RuleResult.skipped(NAME, max, "Net margin unavailable");
        }
        String value = "Net margin " + percent(margin);
        if (margin.compareTo(config.highMargin()) >= 0) {
            return RuleResult.applied(NAME, Tier.STRONG, max, value + " is at or above " + percent(config.highMargin()));
        }
        if (margin.compareTo(config.moderateMargin()) >= 0) {
            return RuleResult.applied(NAME, Tier.GOOD, max, value + " is at or above " + percent(config.moderateMargin()));
        }
        if (margin.signum() > 0) {
            return RuleResult.applied(NAME, Tier.WEAK, max, value + " is positive but below " + percent(config.moderateMargin()));
        }
        return RuleResult.applied(NAME, Tier.POOR, max, value + " is not positive");
    }
}
