package com.service.market_lens.marketdata.alphavantage;

import com.service.market_lens.marketdata.CompanyFundamentals;
import com.service.market_lens.marketdata.EarningsReport;
import com.service.market_lens.marketdata.IndexSnapshot;
import com.service.market_lens.marketdata.MarketDataProvider;
import com.service.market_lens.marketdata.ProviderRateLimitedException;
import com.service.market_lens.marketdata.ProviderUnavailableException;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.BalanceReport;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.CashFlowReport;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.EarningsResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.GlobalQuoteResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.IncomeReport;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.SmaResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.StatementsResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.SymbolSearchResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.TimeSeriesDailyResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link MarketDataProvider} backed by Alpha Vantage's free tier.
 * <p>
 * Every call goes through {@link #query}: throttle → HTTP GET → HTTP-status mapping → body classification
 * ({@link AlphaVantageResponses}) → DTO. Mapping to domain records is delegated to {@link AlphaVantageMapper}.
 * <p>
 * Request cost (free tier: 25/day): fundamentals 5, earnings 2, each index 2. The quote is fetched first so an
 * unknown ticker costs one request, not five.
 */
final class AlphaVantageMarketDataProvider implements MarketDataProvider {

    private static final String PROVIDER = AlphaVantageConfiguration.PROVIDER;
    private static final int SMA_PERIOD = 200;

    private final RestClient restClient;
    private final JsonMapper json;
    private final RequestThrottle throttle;
    private final Clock clock;

    AlphaVantageMarketDataProvider(RestClient restClient, JsonMapper json, RequestThrottle throttle, Clock clock) {
        this.restClient = Objects.requireNonNull(restClient);
        this.json = Objects.requireNonNull(json);
        this.throttle = Objects.requireNonNull(throttle);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public CompanyFundamentals getFundamentals(String ticker) {
        var quote = query(ticker, GlobalQuoteResponse.class, "function", "GLOBAL_QUOTE", "symbol", ticker);
        var search = query(ticker, SymbolSearchResponse.class, "function", "SYMBOL_SEARCH", "keywords", ticker);
        StatementsResponse<IncomeReport> income =
                query(ticker, new TypeReference<>() { }, "function", "INCOME_STATEMENT", "symbol", ticker);
        StatementsResponse<BalanceReport> balance =
                query(ticker, new TypeReference<>() { }, "function", "BALANCE_SHEET", "symbol", ticker);
        StatementsResponse<CashFlowReport> cashFlow =
                query(ticker, new TypeReference<>() { }, "function", "CASH_FLOW", "symbol", ticker);
        return AlphaVantageMapper.toFundamentals(ticker, search, quote, income, balance, cashFlow, clock.instant());
    }

    @Override
    public List<EarningsReport> getRecentEarnings(String ticker, int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1, was " + count);
        }
        var earnings = query(ticker, EarningsResponse.class, "function", "EARNINGS", "symbol", ticker);
        StatementsResponse<IncomeReport> income =
                query(ticker, new TypeReference<>() { }, "function", "INCOME_STATEMENT", "symbol", ticker);
        return AlphaVantageMapper.toEarnings(ticker, earnings, income, count);
    }

    /** Names are left {@code null}; display names for the ETF proxies come from configuration (Phase 4). */
    @Override
    public List<IndexSnapshot> getIndices(List<String> symbols) {
        return symbols.stream()
                .map(symbol -> {
                    var series = query(symbol, TimeSeriesDailyResponse.class,
                            "function", "TIME_SERIES_DAILY", "symbol", symbol, "outputsize", "compact");
                    var sma = query(symbol, SmaResponse.class,
                            "function", "SMA", "symbol", symbol, "interval", "daily",
                            "time_period", String.valueOf(SMA_PERIOD), "series_type", "close");
                    return AlphaVantageMapper.toIndexSnapshot(symbol, null, series, sma);
                })
                .toList();
    }

    // ------------------------------------------------------------------ plumbing

    private <T> T query(String symbol, Class<T> type, String... params) {
        JsonNode body = fetch(symbol, params);
        return convert(body, params[1], b -> json.treeToValue(b, type));
    }

    private <T> T query(String symbol, TypeReference<T> type, String... params) {
        JsonNode body = fetch(symbol, params);
        return convert(body, params[1], b -> json.treeToValue(b, json.constructType(type)));
    }

    /**
     * One GET to {@code /query}. Parameters go in as URI <em>variables</em> ({@code ?function={function}}), so
     * Spring encodes their values fully; the API key is added later by {@link ApiKeyInterceptor}.
     *
     * @param params alternating name, value; the first pair must be {@code function}
     */
    private JsonNode fetch(String symbol, String... params) {
        Map<String, String> vars = new LinkedHashMap<>();
        for (int i = 0; i < params.length; i += 2) {
            vars.put(params[i], params[i + 1]);
        }
        String function = vars.get("function");
        String template = vars.keySet().stream()
                .map(name -> name + "={" + name + "}")
                .collect(Collectors.joining("&", "/query?", ""));

        throttle.acquire();
        String raw;
        try {
            raw = restClient.get()
                    .uri(template, vars)
                    .retrieve()
                    .onStatus(status -> status.value() == HttpStatus.TOO_MANY_REQUESTS.value(), (request, response) -> {
                        throw new ProviderRateLimitedException(PROVIDER, retryAfter(response.getHeaders().getFirst("Retry-After")));
                    })
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new ProviderUnavailableException(PROVIDER,
                                "HTTP " + response.getStatusCode().value() + " for " + function, null);
                    })
                    .body(String.class);
        } catch (RestClientException e) {
            // timeouts, connection failures, unreadable responses. The URI in e's message has no API key.
            throw new ProviderUnavailableException(PROVIDER, "request failed for " + function, e);
        }

        JsonNode body;
        try {
            body = raw == null ? null : json.readTree(raw);
        } catch (JacksonException e) {
            throw new ProviderUnavailableException(PROVIDER, "unreadable response for " + function, e);
        }
        AlphaVantageResponses.check(function, symbol, body);
        return body;
    }

    private interface Converter<T> {
        T convert(JsonNode body);
    }

    private static <T> T convert(JsonNode body, String function, Converter<T> converter) {
        try {
            return converter.convert(body);
        } catch (JacksonException e) {
            throw new ProviderUnavailableException(PROVIDER, "unexpected response shape for " + function, e);
        }
    }

    /** {@code Retry-After} in seconds; the HTTP-date form and junk are ignored ({@code null}). */
    static @Nullable Duration retryAfter(@Nullable String header) {
        if (header == null) {
            return null;
        }
        try {
            long seconds = Long.parseLong(header.strip());
            return seconds >= 0 ? Duration.ofSeconds(seconds) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
