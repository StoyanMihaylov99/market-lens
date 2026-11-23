package com.service.market_lens.marketdata.alphavantage;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.service.market_lens.config.MarketLensProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real {@code RestClient} built by {@link AlphaVantageConfiguration} against an in-process
 * WireMock server: no Spring context, no network.
 */
@WireMockTest
class AlphaVantageRestClientTest {

    private static final String KEY = "test-secret-key";

    private static RestClient client(WireMockRuntimeInfo wm, String apiKey, Duration readTimeout) {
        var props = new AlphaVantageProperties(URI.create(wm.getHttpBaseUrl()), Duration.ofSeconds(1), readTimeout,
                Duration.ZERO);
        var app = new MarketLensProperties(
                new MarketLensProperties.MarketData(AlphaVantageConfiguration.PROVIDER, apiKey),
                new MarketLensProperties.Llm("llama3.2", null),
                new MarketLensProperties.Cache(Duration.ofMinutes(60)));
        return new AlphaVantageConfiguration().alphaVantageRestClient(RestClient.builder(), props, app);
    }

    private static String globalQuote(RestClient client) {
        return client.get()
                .uri("/query?function={f}&symbol={s}", "GLOBAL_QUOTE", "AAPL")
                .retrieve()
                .body(String.class);
    }

    @Test
    void addsApiKeyAndKeepsOtherParams(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/query")).willReturn(okJson("{\"Global Quote\":{}}")));

        assertThat(globalQuote(client(wm, KEY, Duration.ofSeconds(2)))).isEqualTo("{\"Global Quote\":{}}");

        verify(getRequestedFor(urlPathEqualTo("/query"))
                .withQueryParam("function", equalTo("GLOBAL_QUOTE"))
                .withQueryParam("symbol", equalTo("AAPL"))
                .withQueryParam("apikey", equalTo(KEY)));
    }

    @Test
    void encodesKeyWithSpecialCharacters(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/query")).willReturn(okJson("{}")));

        globalQuote(client(wm, "a+b&c=d", Duration.ofSeconds(2)));

        // WireMock decodes query params, so seeing the original value proves it was encoded exactly once.
        verify(getRequestedFor(urlPathEqualTo("/query")).withQueryParam("apikey", equalTo("a+b&c=d")));
    }

    @Test
    void timeoutMessageNeverContainsApiKey(WireMockRuntimeInfo wm) {
        stubFor(get(urlPathEqualTo("/query")).willReturn(okJson("{}").withFixedDelay(1_000)));

        assertThatThrownBy(() -> globalQuote(client(wm, KEY, Duration.ofMillis(200))))
                .isInstanceOf(ResourceAccessException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(KEY).doesNotContain("apikey"));
    }

    @Test
    void missingKeyFailsBeforeAnyRequestIsSent(WireMockRuntimeInfo wm) {
        assertThatThrownBy(() -> globalQuote(client(wm, " ", Duration.ofSeconds(2))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MARKET_DATA_API_KEY");

        verify(0, anyRequestedFor(anyUrl()));
    }
}
