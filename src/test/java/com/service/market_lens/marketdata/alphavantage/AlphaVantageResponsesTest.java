package com.service.market_lens.marketdata.alphavantage;

import com.service.market_lens.marketdata.ProviderRateLimitedException;
import com.service.market_lens.marketdata.ProviderUnavailableException;
import com.service.market_lens.marketdata.TickerNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;

import static com.service.market_lens.marketdata.alphavantage.AlphaVantageResponses.check;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlphaVantageResponsesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }

    private static JsonNode fixture(String name) {
        try (InputStream in = AlphaVantageResponsesTest.class.getResourceAsStream("/fixtures/alphavantage/" + name)) {
            return JSON.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"global_quote-AAPL.json", "income_statement-PLUG.json", "earnings-AAPL.json",
            "symbol_search-AAPL.json", "time_series_daily-SPY.json", "sma200-SPY.json"})
    void realDataPasses(String fixture) {
        assertThatCode(() -> check("FN", "X", fixture(fixture))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"global_quote-UNKNOWNXYZ.json", "income_statement-UNKNOWNXYZ.json",
            "time_series_daily-UNKNOWNXYZ.json"})
    void allThreeRecordedUnknownTickerShapesAreNotFound(String fixture) {
        assertThatThrownBy(() -> check("FN", "UNKNOWNXYZ", fixture(fixture)))
                .isInstanceOfSatisfying(TickerNotFoundException.class,
                        e -> assertThat(e.ticker()).isEqualTo("UNKNOWNXYZ"));
    }

    @Test
    void emptySearchResultIsNotFound() {
        assertThatThrownBy(() -> check("SYMBOL_SEARCH", "NOPE", json("{\"bestMatches\": []}")))
                .isInstanceOf(TickerNotFoundException.class);
    }

    @Test
    void knownCompanyWithoutDataIsNotAnError() {
        var body = json("{\"symbol\": \"NEWCO\", \"annualEarnings\": [], \"quarterlyEarnings\": []}");

        assertThatCode(() -> check("EARNINGS", "NEWCO", body)).doesNotThrowAnyException();
    }

    @Test
    void premiumInformationIsRateLimitedBecauseBurstsAlsoSayPremium() {
        // observed 2026-11-23: free SYMBOL_SEARCH answered this when called 1 s after another request
        var body = json("{\"Information\": \"Thank you for using Alpha Vantage! This is a premium endpoint.\"}");

        assertThatThrownBy(() -> check("SYMBOL_SEARCH", "AAPL", body))
                .isInstanceOf(ProviderRateLimitedException.class);
    }

    @Test
    void otherInformationIsRateLimited() {
        var body = json("{\"Information\": \"Our standard API rate limit is 25 requests per day.\"}");

        assertThatThrownBy(() -> check("GLOBAL_QUOTE", "AAPL", body))
                .isInstanceOfSatisfying(ProviderRateLimitedException.class,
                        e -> assertThat(e.retryAfter()).isEmpty());
    }

    @Test
    void noteIsRateLimited() {
        assertThatThrownBy(() -> check("GLOBAL_QUOTE", "AAPL", json("{\"Note\": \"Thank you for using Alpha Vantage!\"}")))
                .isInstanceOf(ProviderRateLimitedException.class);
    }

    @Test
    void recordedDemoKeyInformationIsRateLimited() {
        assertThatThrownBy(() -> check("OVERVIEW", "MSFT", fixture("information-demo-key.json")))
                .isInstanceOf(ProviderRateLimitedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "\"text\"", "42"})
    void nonObjectBodyIsUnavailable(String body) {
        assertThatThrownBy(() -> check("FN", "X", json(body))).isInstanceOf(ProviderUnavailableException.class);
    }

    @Test
    void nullBodyIsUnavailable() {
        assertThatThrownBy(() -> check("FN", "X", null)).isInstanceOf(ProviderUnavailableException.class);
    }
}
