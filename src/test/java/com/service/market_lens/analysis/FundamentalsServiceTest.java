package com.service.market_lens.analysis;

import com.service.market_lens.analysis.scoring.Recommendation;
import com.service.market_lens.analysis.scoring.ScoringTestData;
import com.service.market_lens.marketdata.MarketDataProvider;
import com.service.market_lens.marketdata.ProviderUnavailableException;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageFixtures;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Orchestration only: provider mocked, real metrics and scoring. */
class FundamentalsServiceTest {

    private final MarketDataProvider provider = mock(MarketDataProvider.class);
    private final FundamentalsService service = new FundamentalsService(provider, ScoringTestData.engine());

    @Test
    void computesMetricsAndScoreFromProviderData() {
        when(provider.getFundamentals("AAPL")).thenReturn(AlphaVantageFixtures.fundamentals("AAPL"));

        FundamentalsAnalysis analysis = service.analyze("AAPL");

        assertThat(analysis.ticker()).isEqualTo("AAPL");
        assertThat(analysis.metrics().priceToEarnings()).isEqualByComparingTo("38.6537");
        assertThat(analysis.score().value()).isEqualTo(53);
        assertThat(analysis.score().recommendation()).isEqualTo(Recommendation.HOLD);
        assertThat(analysis.dataAsOf()).isEqualTo(AlphaVantageFixtures.FETCHED_AT);
    }

    @Test
    void providerFailuresPropagateUnchanged() {
        var failure = new ProviderUnavailableException("test", "down", null);
        when(provider.getFundamentals("AAPL")).thenThrow(failure);

        assertThatThrownBy(() -> service.analyze("AAPL")).isSameAs(failure);
    }
}
