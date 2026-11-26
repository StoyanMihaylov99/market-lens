package com.service.market_lens.analysis.scoring;

import com.service.market_lens.marketdata.alphavantage.AlphaVantageFixtures;
import com.service.market_lens.metrics.MetricsEngine;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static com.service.market_lens.metrics.ComputedMetricsBuilder.complete;
import static com.service.market_lens.metrics.ComputedMetricsBuilder.metrics;
import static org.assertj.core.api.Assertions.assertThat;

class ScoringEngineTest {

    private final ScoringEngine engine = ScoringTestData.engine();

    /** A rule that always returns the given points (or is skipped when {@code points} is null). */
    private static ScoringRule fixed(String name, Integer points, int max) {
        return metrics -> points == null ? RuleResult.skipped(name, max, "n/a")
                : new RuleResult(name, true, points, max, "fixed");
    }

    private static ScoringEngine engineOf(ScoringRule... rules) {
        ScoringProperties defaults = ScoringTestData.properties();
        return new ScoringEngine(List.of(rules), defaults.recommendation(), defaults.confidence());
    }

    @Nested
    class AllInputs {

        @Test
        void everyRuleRunsInFixedOrder() {
            // P/E 20 → 7, D/E 0.8 → 7, FCF yield 4% → 7, net margin 15% → 7: 28 / 40 = 70
            Score score = engine.score(complete().build());

            assertThat(score.rules()).extracting(RuleResult::rule)
                    .containsExactly("valuation", "leverage", "cash-flow", "profitability");
            assertThat(score.rules()).allMatch(RuleResult::applied);
            assertThat(score.value()).isEqualTo(70);
            assertThat(score.recommendation()).isEqualTo(Recommendation.BUY);
            assertThat(score.confidence()).isEqualTo(Confidence.HIGH);
            assertThat(score.coverage()).isEqualTo(new BigDecimal("1.0000"));
        }

        @Test
        void strongCompanyScoresHundred() {
            Score score = engine.score(metrics().netIncomeTtm("1").priceToEarnings("10").debtToEquity("0.1")
                    .freeCashFlowTtm("1").freeCashFlowYield("0.08").netMargin("0.3").build());
            assertThat(score.value()).isEqualTo(100);
            assertThat(score.recommendation()).isEqualTo(Recommendation.BUY);
        }

        @Test
        void weakCompanyScoresZero() {
            Score score = engine.score(metrics().netIncomeTtm("-1").debtToEquity("5")
                    .freeCashFlowTtm("-1").netMargin("-0.2").build());
            assertThat(score.value()).isZero();
            assertThat(score.recommendation()).isEqualTo(Recommendation.SELL);
            assertThat(score.confidence()).isEqualTo(Confidence.HIGH);
        }
    }

    @Nested
    class PartialInputs {

        @Test
        void skippedRulesLeaveTheDenominatorAndLowerConfidence() {
            // only valuation (P/E 10 → 10) and leverage (D/E 0.8 → 7) can run: 17 / 20 = 85
            Score score = engine.score(metrics().netIncomeTtm("1").priceToEarnings("10").debtToEquity("0.8").build());
            assertThat(score.value()).isEqualTo(85);
            assertThat(score.coverage()).isEqualTo(new BigDecimal("0.5000"));
            assertThat(score.confidence()).isEqualTo(Confidence.MEDIUM);
            assertThat(score.recommendation()).isEqualTo(Recommendation.BUY);
            assertThat(score.rules()).filteredOn(r -> !r.applied()).extracting(RuleResult::rule)
                    .containsExactly("cash-flow", "profitability");
        }

        @Test
        void threeOfFourRulesIsMediumConfidence() {
            Score score = engine.score(complete().debtToEquity(null).build());
            assertThat(score.coverage()).isEqualTo(new BigDecimal("0.7500"));
            assertThat(score.confidence()).isEqualTo(Confidence.MEDIUM);
            assertThat(score.value()).isEqualTo(70); // 21 / 30
        }

        @Test
        void lowConfidenceAlwaysHolds() {
            // one strong rule alone would be 100 = BUY, but 1 of 4 rules is too little evidence
            Score score = engine.score(metrics().debtToEquity("0.1").build());
            assertThat(score.value()).isEqualTo(100);
            assertThat(score.confidence()).isEqualTo(Confidence.LOW);
            assertThat(score.recommendation()).isEqualTo(Recommendation.HOLD);
        }
    }

    @Nested
    class NoInputs {

        @Test
        void nothingToScoreGivesNoValueLowConfidenceAndHold() {
            Score score = engine.score(metrics().build());
            assertThat(score.value()).isNull();
            assertThat(score.coverage()).isEqualTo(new BigDecimal("0.0000"));
            assertThat(score.confidence()).isEqualTo(Confidence.LOW);
            assertThat(score.recommendation()).isEqualTo(Recommendation.HOLD);
            assertThat(score.rules()).hasSize(4).noneMatch(RuleResult::applied);
        }

        @Test
        void engineWithoutRules() {
            Score score = engineOf().score(metrics().build());
            assertThat(score.value()).isNull();
            assertThat(score.coverage()).isEqualTo(new BigDecimal("0.0000"));
            assertThat(score.recommendation()).isEqualTo(Recommendation.HOLD);
        }
    }

    @Nested
    class Thresholds {

        @ParameterizedTest(name = "score {0} → {1}")
        @CsvSource({"100, BUY", "65, BUY", "64, HOLD", "41, HOLD", "40, SELL", "0, SELL"})
        void recommendationBoundariesAreInclusive(int points, Recommendation expected) {
            Score score = engineOf(fixed("only", points, 100)).score(metrics().build());
            assertThat(score.value()).isEqualTo(points);
            assertThat(score.recommendation()).isEqualTo(expected);
        }

        @Test
        void valueIsRoundedHalfUp() {
            // 21 / 40 = 52.5 → 53
            assertThat(engineOf(fixed("a", 21, 40)).score(metrics().build()).value()).isEqualTo(53);
            // 1 / 3 = 33.33 → 33
            assertThat(engineOf(fixed("a", 1, 3)).score(metrics().build()).value()).isEqualTo(33);
        }

        @ParameterizedTest(name = "{0} of 10 rules → {1}")
        @CsvSource({"10, HIGH", "8, HIGH", "7, MEDIUM", "5, MEDIUM", "4, LOW", "1, LOW"})
        void confidenceBoundariesAreInclusive(int applied, Confidence expected) {
            ScoringRule[] rules = new ScoringRule[10];
            for (int i = 0; i < rules.length; i++) {
                rules[i] = fixed("r" + i, i < applied ? 5 : null, 10);
            }
            assertThat(engineOf(rules).score(metrics().build()).confidence()).isEqualTo(expected);
        }
    }

    /** Golden: recorded fixtures → metrics → score. Points per rule follow from the metrics in GoldenMetricsTest. */
    @Nested
    class Golden {

        @Test
        void apple() {
            Score score = engine.score(MetricsEngine.compute(AlphaVantageFixtures.fundamentals("AAPL")));
            // P/E 38.65 → 0, D/E 0.71 → 7, FCF yield 2.66% → 4, net margin 27.60% → 10: 21 / 40 = 52.5 → 53
            assertThat(score.rules()).extracting(RuleResult::points).containsExactly(0, 7, 4, 10);
            assertThat(score.value()).isEqualTo(53);
            assertThat(score.recommendation()).isEqualTo(Recommendation.HOLD);
            assertThat(score.confidence()).isEqualTo(Confidence.HIGH);
        }

        @Test
        void plugPower() {
            Score score = engine.score(MetricsEngine.compute(AlphaVantageFixtures.fundamentals("PLUG")));
            // losses → 0, D/E 1.26 → 4, negative FCF → 0, negative margin → 0: 4 / 40 = 10
            assertThat(score.rules()).extracting(RuleResult::points).containsExactly(0, 4, 0, 0);
            assertThat(score.value()).isEqualTo(10);
            assertThat(score.recommendation()).isEqualTo(Recommendation.SELL);
            assertThat(score.confidence()).isEqualTo(Confidence.HIGH);
        }
    }
}
