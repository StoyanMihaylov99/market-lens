package com.service.market_lens.analysis.scoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariants of the scoring value types. */
class ScoringModelTest {

    @ParameterizedTest(name = "{0} of {1} points")
    @CsvSource({"-1, 10", "11, 10", "0, -1"})
    void ruleResultPointsMustBeWithinRange(int points, int max) {
        assertThatThrownBy(() -> new RuleResult("r", true, points, max, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void skippedRuleCannotEarnPoints() {
        assertThatThrownBy(() -> new RuleResult("r", false, 1, 10, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThat(RuleResult.skipped("r", 10, "x").points()).isZero();
    }

    @ParameterizedTest(name = "{0} of 10 → {1}")
    @CsvSource({"STRONG, 10", "GOOD, 7", "WEAK, 4", "POOR, 0"})
    void tierPoints(Tier tier, int points) {
        assertThat(tier.of(10)).isEqualTo(points);
    }

    @ParameterizedTest(name = "score {0}")
    @CsvSource({"-1", "101"})
    void scoreValueMustBeWithinRange(int value) {
        assertThatThrownBy(() -> new Score(value, Recommendation.HOLD, Confidence.LOW, BigDecimal.ZERO, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scoreRulesAreImmutableCopies() {
        List<RuleResult> rules = new ArrayList<>(List.of(RuleResult.skipped("r", 10, "x")));
        Score score = new Score(null, Recommendation.HOLD, Confidence.LOW, BigDecimal.ZERO, rules);
        rules.clear();
        assertThat(score.rules()).hasSize(1);
        assertThatThrownBy(() -> score.rules().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
