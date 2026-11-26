package com.service.market_lens.analysis.scoring;

import java.util.Objects;

/**
 * Outcome of one scoring rule.
 *
 * @param rule      stable rule id, e.g. {@code valuation}
 * @param applied   {@code false} when the rule's inputs were missing; a skipped rule earns nothing and is left out
 *                  of the score's denominator, so missing data lowers {@link Score#confidence()}, not the score
 * @param points    points earned, {@code 0..maxPoints}; always {@code 0} when skipped
 * @param maxPoints points the rule is worth (its weight)
 * @param rationale human-readable reason, e.g. {@code "P/E 38.65 is above 35"}
 */
public record RuleResult(String rule, boolean applied, int points, int maxPoints, String rationale) {

    public RuleResult {
        Objects.requireNonNull(rule, "rule");
        Objects.requireNonNull(rationale, "rationale");
        if (maxPoints < 0 || points < 0 || points > maxPoints) {
            throw new IllegalArgumentException("points must be within 0.." + maxPoints + ", was " + points);
        }
        if (!applied && points != 0) {
            throw new IllegalArgumentException("a skipped rule cannot earn points");
        }
    }

    static RuleResult applied(String rule, Tier tier, int maxPoints, String rationale) {
        return new RuleResult(rule, true, tier.of(maxPoints), maxPoints, rationale);
    }

    static RuleResult skipped(String rule, int maxPoints, String rationale) {
        return new RuleResult(rule, false, 0, maxPoints, rationale);
    }
}
