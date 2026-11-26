package com.service.market_lens.analysis.scoring;

import com.service.market_lens.metrics.ComputedMetrics;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.service.market_lens.metrics.ComputedMetricsBuilder.metrics;
import static org.assertj.core.api.Assertions.assertThat;

/** Each rule in isolation, with the default thresholds; boundaries are inclusive as documented. */
class ScoringRulesTest {

    private static final ScoringProperties CONFIG = ScoringTestData.properties();

    @Nested
    class Valuation {

        private final ValuationRule rule = new ValuationRule(CONFIG.valuation());

        @ParameterizedTest(name = "P/E {0} → {1} points")
        @CsvSource({
                "8, 10", "15, 10",          // ≤ 15 strong
                "15.0001, 7", "25, 7",      // ≤ 25 good
                "25.01, 4", "35, 4",        // ≤ 35 weak
                "35.0001, 0", "120, 0"})    // above: poor
        void bands(String pe, int points) {
            RuleResult result = rule.evaluate(metrics().netIncomeTtm("1").priceToEarnings(pe).build());
            assertThat(result.applied()).isTrue();
            assertThat(result.points()).isEqualTo(points);
            assertThat(result.maxPoints()).isEqualTo(10);
            assertThat(result.rule()).isEqualTo("valuation");
        }

        @Test
        void rationaleNamesValueAndThreshold() {
            assertThat(rule.evaluate(metrics().netIncomeTtm("1").priceToEarnings("38.6537").build()).rationale())
                    .isEqualTo("P/E 38.6537 is above 35");
            assertThat(rule.evaluate(metrics().netIncomeTtm("1").priceToEarnings("12.5000").build()).rationale())
                    .isEqualTo("P/E 12.5 is at or below 15");
        }

        @ParameterizedTest(name = "net income {0}")
        @CsvSource({"0", "-5"})
        void lossMakingCompanyEarnsNothingInsteadOfBeingSkipped(String netIncome) {
            RuleResult result = rule.evaluate(metrics().netIncomeTtm(netIncome).build());
            assertThat(result.applied()).isTrue();
            assertThat(result.points()).isZero();
            assertThat(result.rationale()).contains("No positive TTM earnings");
        }

        @Test
        void skippedWithoutPe() {
            assertSkipped(rule.evaluate(metrics().netIncomeTtm("1").build()));
            assertSkipped(rule.evaluate(metrics().build()));
        }
    }

    @Nested
    class Leverage {

        private final LeverageRule rule = new LeverageRule(CONFIG.leverage());

        @ParameterizedTest(name = "D/E {0} → {1} points")
        @CsvSource({
                "0, 10", "0.5, 10",
                "0.5001, 7", "1.0, 7",
                "1.0001, 4", "2.0, 4",
                "2.0001, 0", "9, 0"})
        void bands(String de, int points) {
            RuleResult result = rule.evaluate(metrics().debtToEquity(de).build());
            assertThat(result.applied()).isTrue();
            assertThat(result.points()).isEqualTo(points);
            assertThat(result.rule()).isEqualTo("leverage");
        }

        @Test
        void skippedWithoutDebtToEquity() {
            assertSkipped(rule.evaluate(metrics().build()));
        }
    }

    @Nested
    class CashFlow {

        private final CashFlowRule rule = new CashFlowRule(CONFIG.cashFlow());

        @ParameterizedTest(name = "FCF yield {0} → {1} points")
        @CsvSource({
                "0.12, 10", "0.05, 10",
                "0.0499, 7", "0.03, 7",
                "0.0299, 4", "0.0001, 4"})
        void bandsForPositiveFreeCashFlow(String yield, int points) {
            RuleResult result = rule.evaluate(metrics().freeCashFlowTtm("100").freeCashFlowYield(yield).build());
            assertThat(result.applied()).isTrue();
            assertThat(result.points()).isEqualTo(points);
            assertThat(result.rule()).isEqualTo("cash-flow");
        }

        @Test
        void rationaleShowsPercentages() {
            assertThat(rule.evaluate(metrics().freeCashFlowTtm("100").freeCashFlowYield("0.0266").build()).rationale())
                    .isEqualTo("FCF yield 2.66% is positive but below 3.00%");
        }

        @ParameterizedTest(name = "FCF {0}")
        @CsvSource({"0", "-100"})
        void nonPositiveFreeCashFlowEarnsNothing(String fcf) {
            RuleResult result = rule.evaluate(metrics().freeCashFlowTtm(fcf).freeCashFlowYield("-0.01").build());
            assertThat(result.applied()).isTrue();
            assertThat(result.points()).isZero();
        }

        @Test
        void positiveFreeCashFlowWithoutYieldIsSkipped() {
            // e.g. an ADR whose price and statements use different currencies: cannot be graded, so not penalised
            assertSkipped(rule.evaluate(metrics().freeCashFlowTtm("100").build()));
        }

        @Test
        void nonPositiveFreeCashFlowWithoutYieldStillEarnsNothing() {
            RuleResult result = rule.evaluate(metrics().freeCashFlowTtm("-100").build());
            assertThat(result.applied()).isTrue();
            assertThat(result.points()).isZero();
        }

        @Test
        void skippedWithoutFreeCashFlow() {
            assertSkipped(rule.evaluate(metrics().freeCashFlowYield("0.1").build()));
        }
    }

    @Nested
    class Profitability {

        private final ProfitabilityRule rule = new ProfitabilityRule(CONFIG.profitability());

        @ParameterizedTest(name = "net margin {0} → {1} points")
        @CsvSource({
                "0.35, 10", "0.20, 10",
                "0.1999, 7", "0.10, 7",
                "0.0999, 4", "0.0001, 4",
                "0, 0", "-1.5368, 0"})
        void bands(String margin, int points) {
            RuleResult result = rule.evaluate(metrics().netMargin(margin).build());
            assertThat(result.applied()).isTrue();
            assertThat(result.points()).isEqualTo(points);
            assertThat(result.rule()).isEqualTo("profitability");
        }

        @Test
        void skippedWithoutNetMargin() {
            assertSkipped(rule.evaluate(metrics().build()));
        }
    }

    @Test
    void weightScalesTierPoints() {
        var rule = new LeverageRule(new ScoringProperties.Leverage(5, CONFIG.leverage().low(),
                CONFIG.leverage().moderate(), CONFIG.leverage().high()));
        ComputedMetrics good = metrics().debtToEquity("0.8").build();
        assertThat(rule.evaluate(good).points()).isEqualTo(4);   // 0.7 × 5 = 3.5 → 4 (half up)
        assertThat(rule.evaluate(good).maxPoints()).isEqualTo(5);
    }

    private static void assertSkipped(RuleResult result) {
        assertThat(result.applied()).isFalse();
        assertThat(result.points()).isZero();
        assertThat(result.maxPoints()).isEqualTo(10);
        assertThat(result.rationale()).contains("unavailable");
    }
}
