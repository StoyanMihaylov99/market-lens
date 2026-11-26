package com.service.market_lens.analysis.scoring;

import java.math.BigDecimal;

/**
 * Scoring configuration equal to the defaults in {@code scoring.yaml} ({@link ScoringPropertiesTest} checks
 * that they match), and an engine built from it. Public so other features' tests can score metrics.
 */
public final class ScoringTestData {

    private ScoringTestData() {
    }

    static ScoringProperties properties() {
        return new ScoringProperties(
                new ScoringProperties.RecommendationThresholds(65, 40),
                new ScoringProperties.ConfidenceThresholds(d("0.8"), d("0.5")),
                new ScoringProperties.Valuation(10, d("15"), d("25"), d("35")),
                new ScoringProperties.Leverage(10, d("0.5"), d("1.0"), d("2.0")),
                new ScoringProperties.CashFlow(10, d("0.03"), d("0.05")),
                new ScoringProperties.Profitability(10, d("0.10"), d("0.20")));
    }

    public static ScoringEngine engine() {
        return ScoringEngine.of(properties());
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }
}
