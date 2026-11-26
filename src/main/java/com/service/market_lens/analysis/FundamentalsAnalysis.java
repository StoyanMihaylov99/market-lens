package com.service.market_lens.analysis;

import com.service.market_lens.analysis.scoring.Score;
import com.service.market_lens.metrics.ComputedMetrics;

import java.time.Instant;
import java.util.Objects;

/**
 * Deterministic view of one company: computed metrics plus the rule-based score. No LLM involved.
 *
 * @param dataAsOf when the underlying market data was fetched from the provider
 */
public record FundamentalsAnalysis(String ticker, ComputedMetrics metrics, Score score, Instant dataAsOf) {

    public FundamentalsAnalysis {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(dataAsOf, "dataAsOf");
    }
}
