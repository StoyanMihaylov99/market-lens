package com.service.market_lens.analysis.scoring;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic rule-based score: the base view the LLM later explains but never changes.
 *
 * @param value          0–100 = earned ÷ available points of the rules that ran; {@code null} when no rule could run
 * @param recommendation from the value's band; always {@link Recommendation#HOLD} when confidence is
 *                       {@link Confidence#LOW}, because a BUY or SELL resting on a fraction of the model would mislead
 * @param confidence     from {@code coverage}
 * @param coverage       share of rules that could run, a decimal fraction ({@code 0.75} = 3 of 4)
 * @param rules          every rule's outcome, skipped ones included, in evaluation order
 */
public record Score(
        @Nullable Integer value,
        Recommendation recommendation,
        Confidence confidence,
        BigDecimal coverage,
        List<RuleResult> rules) {

    public Score {
        Objects.requireNonNull(recommendation, "recommendation");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(coverage, "coverage");
        if (value != null && (value < 0 || value > 100)) {
            throw new IllegalArgumentException("score must be within 0..100, was " + value);
        }
        rules = List.copyOf(rules);
    }
}
