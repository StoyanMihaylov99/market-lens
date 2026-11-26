package com.service.market_lens.analysis.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The bands every rule maps its metric into, as a share of the rule's max points. Shared so all rules grade on
 * the same scale; thresholds that decide the band are per rule, in {@link ScoringProperties}.
 */
enum Tier {
    /** Best band: full points. */
    STRONG("1.0"),
    /** Good: 70%. */
    GOOD("0.7"),
    /** Weak but acceptable: 40%. */
    WEAK("0.4"),
    /** Poor: nothing. */
    POOR("0");

    private final BigDecimal share;

    Tier(String share) {
        this.share = new BigDecimal(share);
    }

    /** Points for this tier, rounded half up ({@code 0.7 × 5 = 3.5 → 4}). */
    int of(int maxPoints) {
        return share.multiply(BigDecimal.valueOf(maxPoints)).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }
}
