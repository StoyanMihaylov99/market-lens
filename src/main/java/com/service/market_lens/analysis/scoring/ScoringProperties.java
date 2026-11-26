package com.service.market_lens.analysis.scoring;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * Thresholds and weights of the rule-based score ({@code marketlens.scoring.*}); defaults in {@code scoring.yaml}
 * (imported by {@code application.yaml}).
 * <p>
 * Units follow the project rule: fractions are decimal ({@code 0.05} = 5%), multiples are plain ({@code 15} = 15×).
 * Each rule's {@code max-points} is its weight; equal weights (decision D4) until Phase 11 backtesting tunes them.
 * Thresholds must be strictly ordered; a misordered config stops the app at startup.
 */
@Validated
@ConfigurationProperties(prefix = "marketlens.scoring")
record ScoringProperties(
        @Valid @NotNull RecommendationThresholds recommendation,
        @Valid @NotNull ConfidenceThresholds confidence,
        @Valid @NotNull Valuation valuation,
        @Valid @NotNull Leverage leverage,
        @Valid @NotNull CashFlow cashFlow,
        @Valid @NotNull Profitability profitability) {

    /** Score (0–100) at or above {@code buy} → BUY; at or below {@code sell} → SELL; otherwise HOLD. */
    record RecommendationThresholds(@NotNull @Min(0) @Max(100) Integer buy,
                                    @NotNull @Min(0) @Max(100) Integer sell) {
        RecommendationThresholds {
            if (buy != null && sell != null && sell >= buy) {
                throw new IllegalArgumentException("marketlens.scoring.recommendation.sell must be below buy");
            }
        }
    }

    /** Coverage = share of rules that could run. At or above {@code high} → HIGH; at or above {@code medium} → MEDIUM. */
    record ConfidenceThresholds(@NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal high,
                                @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal medium) {
        ConfidenceThresholds {
            requireAscending("confidence", medium, high);
        }
    }

    /** P/E bands: ≤ cheap → strong, ≤ fair → good, ≤ expensive → weak, above → poor. */
    record Valuation(@NotNull @Min(1) Integer maxPoints,
                     @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal cheap,
                     @NotNull BigDecimal fair,
                     @NotNull BigDecimal expensive) {
        Valuation {
            requireAscending("valuation", cheap, fair, expensive);
        }
    }

    /** Debt-to-equity bands: ≤ low → strong, ≤ moderate → good, ≤ high → weak, above → poor. */
    record Leverage(@NotNull @Min(1) Integer maxPoints,
                    @NotNull @DecimalMin("0") BigDecimal low,
                    @NotNull BigDecimal moderate,
                    @NotNull BigDecimal high) {
        Leverage {
            requireAscending("leverage", low, moderate, high);
        }
    }

    /** FCF yield bands: ≥ high → strong, ≥ moderate → good, positive → weak, FCF ≤ 0 → poor. */
    record CashFlow(@NotNull @Min(1) Integer maxPoints,
                    @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal moderateYield,
                    @NotNull BigDecimal highYield) {
        CashFlow {
            requireAscending("cash-flow", moderateYield, highYield);
        }
    }

    /** Net margin bands: ≥ high → strong, ≥ moderate → good, positive → weak, ≤ 0 → poor. */
    record Profitability(@NotNull @Min(1) Integer maxPoints,
                         @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal moderateMargin,
                         @NotNull BigDecimal highMargin) {
        Profitability {
            requireAscending("profitability", moderateMargin, highMargin);
        }
    }

    /** Null-tolerant (nulls are reported by {@code @NotNull}); throws if the given values are not strictly increasing. */
    private static void requireAscending(String rule, BigDecimal... values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i - 1] != null && values[i] != null && values[i - 1].compareTo(values[i]) >= 0) {
                throw new IllegalArgumentException(
                        "marketlens.scoring." + rule + " thresholds must be strictly increasing");
            }
        }
    }
}
