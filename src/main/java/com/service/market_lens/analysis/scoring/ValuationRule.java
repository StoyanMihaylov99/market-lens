package com.service.market_lens.analysis.scoring;

import com.service.market_lens.metrics.ComputedMetrics;

import java.math.BigDecimal;

import static com.service.market_lens.analysis.scoring.ScoringRule.number;

/** Cheaper on GAAP P/E scores higher. A company without positive TTM earnings earns nothing here. */
final class ValuationRule implements ScoringRule {

    static final String NAME = "valuation";

    private final ScoringProperties.Valuation config;

    ValuationRule(ScoringProperties.Valuation config) {
        this.config = config;
    }

    @Override
    public RuleResult evaluate(ComputedMetrics metrics) {
        int max = config.maxPoints();
        BigDecimal netIncome = metrics.netIncomeTtm();
        if (netIncome != null && netIncome.signum() <= 0) {
            return RuleResult.applied(NAME, Tier.POOR, max, "No positive TTM earnings, so P/E is not meaningful");
        }
        BigDecimal pe = metrics.priceToEarnings();
        if (pe == null) {
            return RuleResult.skipped(NAME, max, "P/E unavailable");
        }
        String value = "P/E " + number(pe);
        if (pe.compareTo(config.cheap()) <= 0) {
            return RuleResult.applied(NAME, Tier.STRONG, max, value + " is at or below " + number(config.cheap()));
        }
        if (pe.compareTo(config.fair()) <= 0) {
            return RuleResult.applied(NAME, Tier.GOOD, max, value + " is at or below " + number(config.fair()));
        }
        if (pe.compareTo(config.expensive()) <= 0) {
            return RuleResult.applied(NAME, Tier.WEAK, max, value + " is at or below " + number(config.expensive()));
        }
        return RuleResult.applied(NAME, Tier.POOR, max, value + " is above " + number(config.expensive()));
    }
}
