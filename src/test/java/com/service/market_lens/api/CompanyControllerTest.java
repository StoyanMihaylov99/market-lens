package com.service.market_lens.api;

import com.service.market_lens.analysis.FundamentalsAnalysis;
import com.service.market_lens.analysis.FundamentalsService;
import com.service.market_lens.analysis.scoring.ScoringTestData;
import com.service.market_lens.marketdata.ProviderRateLimitedException;
import com.service.market_lens.marketdata.ProviderUnavailableException;
import com.service.market_lens.marketdata.TickerNotFoundException;
import com.service.market_lens.metrics.ComputedMetrics;
import com.service.market_lens.metrics.ComputedMetricsBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Web layer only (@WebMvcTest): controller + ApiExceptionHandler, service mocked. */
@WebMvcTest(CompanyController.class)
class CompanyControllerTest {

    private static final String URL = "/api/v1/companies/{ticker}/fundamentals";
    private static final Instant AS_OF = Instant.parse("2026-11-20T12:00:00Z");

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    FundamentalsService service;

    private static FundamentalsAnalysis analysis() {
        ComputedMetrics metrics = ComputedMetricsBuilder.complete().build();
        return new FundamentalsAnalysis("AAPL", metrics, ScoringTestData.engine().score(metrics), AS_OF);
    }

    @Test
    void returnsMetricsScoreAndRules() {
        when(service.analyze("AAPL")).thenReturn(analysis());

        assertThat(mvc.get().uri(URL, "AAPL"))
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .hasPathSatisfying("$.ticker", v -> v.assertThat().isEqualTo("AAPL"))
                .hasPathSatisfying("$.metrics.priceToEarnings", v -> v.assertThat().asNumber().isEqualTo(20))
                .hasPathSatisfying("$.metrics.marketCap", v -> v.assertThat().isNull())
                .hasPathSatisfying("$.score.value", v -> v.assertThat().asNumber().isEqualTo(70))
                .hasPathSatisfying("$.score.recommendation", v -> v.assertThat().isEqualTo("BUY"))
                .hasPathSatisfying("$.score.confidence", v -> v.assertThat().isEqualTo("HIGH"))
                .hasPathSatisfying("$.score.coverage", v -> v.assertThat().asNumber().isEqualTo(1.0))
                .hasPathSatisfying("$.rules.length()", v -> v.assertThat().asNumber().isEqualTo(4))
                .hasPathSatisfying("$.rules[0].rule", v -> v.assertThat().isEqualTo("valuation"))
                .hasPathSatisfying("$.rules[0].applied", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying("$.rules[0].points", v -> v.assertThat().asNumber().isEqualTo(7))
                .hasPathSatisfying("$.rules[0].rationale", v -> v.assertThat().isEqualTo("P/E 20 is at or below 25"))
                .hasPathSatisfying("$.dataAsOf", v -> v.assertThat().isEqualTo("2026-11-20T12:00:00Z"))
                .hasPathSatisfying("$.disclaimer", v -> v.assertThat().asString().contains("Not financial advice"));
    }

    @Test
    void upperCasesTicker() {
        when(service.analyze("BRK.B")).thenReturn(analysis());

        assertThat(mvc.get().uri(URL, "brk.b")).hasStatusOk();
        verify(service).analyze("BRK.B");
    }

    @Test
    void invalidTickerIs400ProblemWithoutCallingService() {
        // note: "AAPL;x" would NOT test this: Spring strips ";..." from paths (matrix variables) → "AAPL"
        for (String ticker : new String[]{"AAPL!", "<script>", "THIRTEENCHARS", "---", ".", ".AAPL"}) {
            assertThat(mvc.get().uri(URL, ticker))
                    .hasStatus(400)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        }
        verify(service, never()).analyze(anyString());
    }

    @Test
    void acceptsTickersWithDotsDashesAndLeadingDigits() {
        when(service.analyze(anyString())).thenReturn(analysis());

        for (String ticker : new String[]{"0700.hk", "RDS-A", "TWELVECHARSX", "F"}) {
            assertThat(mvc.get().uri(URL, ticker)).as(ticker).hasStatusOk();
        }
        verify(service).analyze("0700.HK");
    }

    @Test
    void unknownTickerIs404Problem() {
        when(service.analyze("NOPE")).thenThrow(new TickerNotFoundException("NOPE"));

        assertThat(mvc.get().uri(URL, "NOPE"))
                .hasStatus(404)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.status", v -> v.assertThat().asNumber().isEqualTo(404))
                .hasPathSatisfying("$.title", v -> v.assertThat().isEqualTo("Ticker not found"))
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString().contains("'NOPE'"))
                .hasPathSatisfying("$.instance", v -> v.assertThat().isEqualTo("/api/v1/companies/NOPE/fundamentals"));
    }

    @Test
    void providerDownIs503ProblemWithoutInternalDetails() {
        when(service.analyze("AAPL")).thenThrow(
                new ProviderUnavailableException("alphavantage", "HTTP 500 from function=INCOME_STATEMENT", null));

        assertThat(mvc.get().uri(URL, "AAPL"))
                .hasStatus(503)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .doesNotContainHeader("Retry-After")
                .bodyJson()
                .hasPathSatisfying("$.title", v -> v.assertThat().isEqualTo("Market data unavailable"))
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString()
                        .doesNotContain("alphavantage", "INCOME_STATEMENT"));
    }

    @Test
    void rateLimitedIs503WithRetryAfterWhenKnown() {
        when(service.analyze("AAPL")).thenThrow(new ProviderRateLimitedException("alphavantage", Duration.ofSeconds(60)));

        assertThat(mvc.get().uri(URL, "AAPL"))
                .hasStatus(503)
                .hasHeader("Retry-After", "60")
                .bodyJson()
                .hasPathSatisfying("$.title", v -> v.assertThat().isEqualTo("Market data rate limit reached"));
    }

    @Test
    void rateLimitedWithoutKnownDelayHasNoRetryAfter() {
        when(service.analyze("AAPL")).thenThrow(new ProviderRateLimitedException("alphavantage", null));

        assertThat(mvc.get().uri(URL, "AAPL")).hasStatus(503).doesNotContainHeader("Retry-After");
    }

    @Test
    void subSecondRetryAfterIsRoundedUpToOneSecond() {
        when(service.analyze("AAPL")).thenThrow(new ProviderRateLimitedException("alphavantage", Duration.ofMillis(200)));

        assertThat(mvc.get().uri(URL, "AAPL")).hasStatus(503).hasHeader("Retry-After", "1");
    }

    @Test
    void fractionalRetryAfterIsRoundedUp() {
        when(service.analyze("AAPL")).thenThrow(new ProviderRateLimitedException("alphavantage", Duration.ofMillis(1800)));

        assertThat(mvc.get().uri(URL, "AAPL")).hasStatus(503).hasHeader("Retry-After", "2");
    }

    @Test
    void unexpectedErrorIs500ProblemWithoutMessage() {
        when(service.analyze("AAPL")).thenThrow(new IllegalStateException("secret internal state"));

        assertThat(mvc.get().uri(URL, "AAPL"))
                .hasStatus(500)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .hasPathSatisfying("$.detail", v -> v.assertThat().asString().doesNotContain("secret"));
    }

    @Test
    void unknownPathIs404Problem() {
        assertThat(mvc.get().uri("/api/v1/companies/AAPL/nothing-here"))
                .hasStatus(404)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
