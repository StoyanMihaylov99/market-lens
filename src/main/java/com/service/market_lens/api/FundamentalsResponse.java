package com.service.market_lens.api;

import com.service.market_lens.analysis.FundamentalsAnalysis;
import com.service.market_lens.analysis.scoring.Confidence;
import com.service.market_lens.analysis.scoring.Recommendation;
import com.service.market_lens.analysis.scoring.RuleResult;
import com.service.market_lens.analysis.scoring.Score;
import com.service.market_lens.metrics.ComputedMetrics;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Body of {@code GET /api/v1/companies/{ticker}/fundamentals}: computed metrics and the rule-based score, no LLM.
 *
 * @param rules    every scoring rule's outcome, including skipped ones
 * @param dataAsOf when the market data was fetched from the provider
 */
public record FundamentalsResponse(
        String ticker,
        ComputedMetrics metrics,
        ScoreSummary score,
        List<RuleResult> rules,
        Instant dataAsOf,
        String disclaimer) {

    static final String DISCLAIMER =
            "For information only. Computed from third-party data that may be incomplete or delayed. Not financial advice.";

    /** The score without its rule list (which is returned as {@code rules}). */
    public record ScoreSummary(@Nullable Integer value, Recommendation recommendation, Confidence confidence,
                               BigDecimal coverage) {
    }

    static FundamentalsResponse from(FundamentalsAnalysis analysis) {
        Score score = analysis.score();
        return new FundamentalsResponse(
                analysis.ticker(),
                analysis.metrics(),
                new ScoreSummary(score.value(), score.recommendation(), score.confidence(), score.coverage()),
                score.rules(),
                analysis.dataAsOf(),
                DISCLAIMER);
    }
}
