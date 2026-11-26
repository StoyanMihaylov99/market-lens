package com.service.market_lens.analysis.scoring;

import com.service.market_lens.metrics.ComputedMetrics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Runs every {@link ScoringRule} and turns the results into a {@link Score}. Deterministic and thread-safe
 * (immutable after construction); Spring creates the single instance in {@link ScoringConfiguration}.
 * <p>
 * {@code value = round(100 × Σ earned ÷ Σ max)} over the rules that ran, rounded half up. Skipped rules lower
 * {@link Score#coverage()} and therefore {@link Score#confidence()}, not the value.
 */
public final class ScoringEngine {

    private static final int FRACTION_SCALE = 4;

    private final List<ScoringRule> rules;
    private final ScoringProperties.RecommendationThresholds recommendation;
    private final ScoringProperties.ConfidenceThresholds confidence;

    ScoringEngine(List<ScoringRule> rules,
                  ScoringProperties.RecommendationThresholds recommendation,
                  ScoringProperties.ConfidenceThresholds confidence) {
        this.rules = List.copyOf(rules);
        this.recommendation = recommendation;
        this.confidence = confidence;
    }

    /** All rules, in a fixed order, configured from {@code properties}. */
    static ScoringEngine of(ScoringProperties properties) {
        return new ScoringEngine(List.of(
                new ValuationRule(properties.valuation()),
                new LeverageRule(properties.leverage()),
                new CashFlowRule(properties.cashFlow()),
                new ProfitabilityRule(properties.profitability())),
                properties.recommendation(), properties.confidence());
    }

    public Score score(ComputedMetrics metrics) {
        List<RuleResult> results = rules.stream().map(rule -> rule.evaluate(metrics)).toList();

        int earned = 0;
        int available = 0;
        int applied = 0;
        for (RuleResult r : results) {
            if (r.applied()) {
                earned += r.points();
                available += r.maxPoints();
                applied++;
            }
        }

        Integer value = available == 0 ? null
                : BigDecimal.valueOf(100L * earned)
                        .divide(BigDecimal.valueOf(available), 0, RoundingMode.HALF_UP)
                        .intValueExact();
        BigDecimal coverage = results.isEmpty() ? BigDecimal.ZERO.setScale(FRACTION_SCALE)
                : BigDecimal.valueOf(applied).divide(BigDecimal.valueOf(results.size()), FRACTION_SCALE, RoundingMode.HALF_EVEN);
        Confidence level = confidenceFor(coverage);

        return new Score(value, recommendationFor(value, level), level, coverage, results);
    }

    private Confidence confidenceFor(BigDecimal coverage) {
        if (coverage.compareTo(confidence.high()) >= 0) {
            return Confidence.HIGH;
        }
        return coverage.compareTo(confidence.medium()) >= 0 ? Confidence.MEDIUM : Confidence.LOW;
    }

    private Recommendation recommendationFor(Integer value, Confidence level) {
        if (value == null || level == Confidence.LOW) {
            return Recommendation.HOLD;
        }
        if (value >= recommendation.buy()) {
            return Recommendation.BUY;
        }
        return value <= recommendation.sell() ? Recommendation.SELL : Recommendation.HOLD;
    }
}
