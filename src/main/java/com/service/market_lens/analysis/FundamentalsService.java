package com.service.market_lens.analysis;

import com.service.market_lens.analysis.scoring.ScoringEngine;
import com.service.market_lens.marketdata.CompanyFundamentals;
import com.service.market_lens.marketdata.MarketDataProvider;
import com.service.market_lens.metrics.ComputedMetrics;
import com.service.market_lens.metrics.MetricsEngine;
import org.springframework.stereotype.Service;

/**
 * Fetches fundamentals, computes metrics and scores them. Also the deterministic half of the later
 * {@code AnalysisService} (Phase 5), which adds the LLM narrative on top.
 * <p>
 * Market data exceptions propagate unchanged; the API layer maps them to HTTP responses.
 */
@Service
public class FundamentalsService {

    private final MarketDataProvider provider;
    private final ScoringEngine scoringEngine;

    FundamentalsService(MarketDataProvider provider, ScoringEngine scoringEngine) {
        this.provider = provider;
        this.scoringEngine = scoringEngine;
    }

    /**
     * @param ticker already validated and upper-cased by the caller (see the {@link MarketDataProvider} contract)
     * @throws com.service.market_lens.marketdata.MarketDataException if the provider fails or does not know the ticker
     */
    public FundamentalsAnalysis analyze(String ticker) {
        CompanyFundamentals fundamentals = provider.getFundamentals(ticker);
        ComputedMetrics metrics = MetricsEngine.compute(fundamentals);
        return new FundamentalsAnalysis(ticker, metrics, scoringEngine.score(metrics), fundamentals.fetchedAt());
    }
}
