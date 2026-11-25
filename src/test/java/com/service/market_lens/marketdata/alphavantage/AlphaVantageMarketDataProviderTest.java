package com.service.market_lens.marketdata.alphavantage;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.service.market_lens.config.MarketLensProperties;
import com.service.market_lens.marketdata.FiscalPeriod;
import com.service.market_lens.marketdata.MarketDataProvider;
import com.service.market_lens.marketdata.ProviderRateLimitedException;
import com.service.market_lens.marketdata.ProviderUnavailableException;
import com.service.market_lens.marketdata.TickerNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The full adapter (RestClient + interceptor + throttle + classification + mapping) against an in-process WireMock
 * server that replays the recorded fixtures. Zero real network calls.
 */
@WireMockTest
class AlphaVantageMarketDataProviderTest {

    private static final String KEY = "test-secret-key";
    private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");

    private MarketDataProvider provider;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wm) {
        provider = provider(wm, Duration.ofSeconds(2));
    }

    private static MarketDataProvider provider(WireMockRuntimeInfo wm, Duration readTimeout) {
        var props = new AlphaVantageProperties(URI.create(wm.getHttpBaseUrl()), Duration.ofSeconds(1), readTimeout,
                Duration.ZERO);
        var app = new MarketLensProperties(
                new MarketLensProperties.MarketData(AlphaVantageConfiguration.PROVIDER, KEY),
                new MarketLensProperties.Llm("llama3.2", null),
                new MarketLensProperties.Cache(Duration.ofMinutes(60)));
        var config = new AlphaVantageConfiguration();
        RestClient client = config.alphaVantageRestClient(RestClient.builder(), props, app);
        return new AlphaVantageMarketDataProvider(client, JsonMapper.builder().build(),
                new RequestThrottle(Duration.ZERO), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ------------------------------------------------------------------ stub helpers

    private static MappingBuilder call(String function, String param, String value) {
        return get(urlPathEqualTo("/query"))
                .withQueryParam("function", equalTo(function))
                .withQueryParam(param, equalTo(value));
    }

    private static void replay(String function, String param, String value, String fixture) {
        stubFor(call(function, param, value).willReturn(okJson(read(fixture))));
    }

    private static String read(String fixture) {
        try (InputStream in = AlphaVantageMarketDataProviderTest.class
                .getResourceAsStream("/fixtures/alphavantage/" + fixture)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void replayFundamentals(String t) {
        replay("GLOBAL_QUOTE", "symbol", t, "global_quote-" + t + ".json");
        replay("SYMBOL_SEARCH", "keywords", t, "symbol_search-" + t + ".json");
        replay("INCOME_STATEMENT", "symbol", t, "income_statement-" + t + ".json");
        replay("BALANCE_SHEET", "symbol", t, "balance_sheet-" + t + ".json");
        replay("CASH_FLOW", "symbol", t, "cash_flow-" + t + ".json");
    }

    // ------------------------------------------------------------------ happy paths

    @Test
    void fundamentalsForApple() {
        replayFundamentals("AAPL");

        var f = provider.getFundamentals("AAPL");

        assertThat(f.profile().name()).isEqualTo("Apple Inc");
        assertThat(f.quote().price()).isEqualByComparingTo("349.21");
        assertThat(f.quote().marketCap()).isEqualByComparingTo("5109053348780");
        assertThat(f.incomeStatements()).hasSize(8);
        assertThat(f.balanceSheets().getFirst().totalDebt()).isEqualByComparingTo("80111000000");
        assertThat(f.cashFlowStatements().getFirst().capitalExpenditure()).isNegative();
        assertThat(f.fetchedAt()).isEqualTo(NOW);

        verify(5, getRequestedFor(urlPathEqualTo("/query")).withQueryParam("apikey", equalTo(KEY)));
    }

    @Test
    void recentEarningsForPlug() {
        replay("EARNINGS", "symbol", "PLUG", "earnings-PLUG.json");
        replay("INCOME_STATEMENT", "symbol", "PLUG", "income_statement-PLUG.json");

        var reports = provider.getRecentEarnings("PLUG", 2);

        assertThat(reports).hasSize(2);
        assertThat(reports.getFirst().periodEnd()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(reports.getFirst().period()).isEqualTo(FiscalPeriod.Q3);
        assertThat(reports.getFirst().epsActual()).isEqualByComparingTo("-0.12");
        assertThat(reports.getFirst().revenueActual()).isEqualByComparingTo("200602000");
        verify(2, getRequestedFor(urlPathEqualTo("/query")));
    }

    @Test
    void indicesKeepRequestedOrder() {
        replay("TIME_SERIES_DAILY", "symbol", "SPY", "time_series_daily-SPY.json");
        replay("SMA", "symbol", "SPY", "sma200-SPY.json");
        // second "index" reuses SPY data under another symbol, just to check ordering
        replay("TIME_SERIES_DAILY", "symbol", "QQQ", "time_series_daily-SPY.json");
        replay("SMA", "symbol", "QQQ", "sma200-SPY.json");

        var snapshots = provider.getIndices(List.of("QQQ", "SPY"));

        assertThat(snapshots).extracting(s -> s.symbol()).containsExactly("QQQ", "SPY");
        assertThat(snapshots.get(1).twoHundredDayAverage()).isEqualByComparingTo("730.2056");
        verify(getRequestedFor(urlPathEqualTo("/query"))
                .withQueryParam("function", equalTo("SMA"))
                .withQueryParam("time_period", equalTo("200"))
                .withQueryParam("interval", equalTo("daily"))
                .withQueryParam("series_type", equalTo("close")));
    }

    // ------------------------------------------------------------------ not found

    @Test
    void unknownTickerCostsOneRequest() {
        replay("GLOBAL_QUOTE", "symbol", "UNKNOWNXYZ", "global_quote-UNKNOWNXYZ.json");

        assertThatThrownBy(() -> provider.getFundamentals("UNKNOWNXYZ"))
                .isInstanceOfSatisfying(TickerNotFoundException.class,
                        e -> assertThat(e.ticker()).isEqualTo("UNKNOWNXYZ"));
        verify(1, getRequestedFor(urlPathEqualTo("/query")));
    }

    @Test
    void unknownIndexSymbolFailsLoudly() {
        replay("TIME_SERIES_DAILY", "symbol", "UNKNOWNXYZ", "time_series_daily-UNKNOWNXYZ.json");

        assertThatThrownBy(() -> provider.getIndices(List.of("UNKNOWNXYZ")))
                .isInstanceOf(TickerNotFoundException.class);
    }

    @Test
    void earningsCountIsValidatedBeforeAnyRequest() {
        assertThatThrownBy(() -> provider.getRecentEarnings("AAPL", 0)).isInstanceOf(IllegalArgumentException.class);
        verify(0, anyRequestedFor(anyUrl()));
    }

    // ------------------------------------------------------------------ vendor errors

    @Test
    void rateLimitBodyWithHttp200() {
        stubFor(call("GLOBAL_QUOTE", "symbol", "AAPL")
                .willReturn(okJson("{\"Information\": \"Our standard API rate limit is 25 requests per day.\"}")));

        assertThatThrownBy(() -> provider.getFundamentals("AAPL")).isInstanceOf(ProviderRateLimitedException.class);
    }

    @Test
    void premiumBodyWithHttp200IsTreatedAsRateLimit() {
        stubFor(call("GLOBAL_QUOTE", "symbol", "AAPL")
                .willReturn(okJson("{\"Information\": \"This is a premium endpoint.\"}")));

        assertThatThrownBy(() -> provider.getFundamentals("AAPL")).isInstanceOf(ProviderRateLimitedException.class);
    }

    @Test
    void http429UsesRetryAfterHeader() {
        stubFor(call("GLOBAL_QUOTE", "symbol", "AAPL")
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));

        assertThatThrownBy(() -> provider.getFundamentals("AAPL"))
                .isInstanceOfSatisfying(ProviderRateLimitedException.class,
                        e -> assertThat(e.retryAfter()).contains(Duration.ofSeconds(30)));
    }

    @Test
    void http500IsUnavailable() {
        stubFor(call("GLOBAL_QUOTE", "symbol", "AAPL").willReturn(serverError()));

        assertThatThrownBy(() -> provider.getFundamentals("AAPL"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void timeoutIsUnavailableAndNeverLeaksKey(WireMockRuntimeInfo wm) {
        stubFor(call("GLOBAL_QUOTE", "symbol", "AAPL").willReturn(okJson("{}").withFixedDelay(1_000)));
        var slow = provider(wm, Duration.ofMillis(200));

        assertThatThrownBy(() -> slow.getFundamentals("AAPL"))
                .isInstanceOf(ProviderUnavailableException.class)
                .satisfies(e -> {
                    for (Throwable t = e; t != null; t = t.getCause()) {
                        assertThat(String.valueOf(t.getMessage())).doesNotContain(KEY);
                    }
                });
    }

    @Test
    void malformedJsonIsUnavailable() {
        stubFor(call("GLOBAL_QUOTE", "symbol", "AAPL")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{not json")));

        assertThatThrownBy(() -> provider.getFundamentals("AAPL"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("unreadable");
    }

    @Test
    void wrongShapeIsUnavailable() {
        stubFor(call("GLOBAL_QUOTE", "symbol", "AAPL")
                .willReturn(okJson("{\"Global Quote\": \"not an object\"}")));

        assertThatThrownBy(() -> provider.getFundamentals("AAPL"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("unexpected response shape");
    }

    @Test
    void retryAfterParsing() {
        assertThat(AlphaVantageMarketDataProvider.retryAfter("30")).isEqualTo(Duration.ofSeconds(30));
        assertThat(AlphaVantageMarketDataProvider.retryAfter("Wed, 21 Oct 2026 07:28:00 GMT")).isNull();
        assertThat(AlphaVantageMarketDataProvider.retryAfter("-5")).isNull();
        assertThat(AlphaVantageMarketDataProvider.retryAfter(null)).isNull();
    }
}
