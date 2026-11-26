package com.service.market_lens.marketdata.alphavantage;

import com.service.market_lens.marketdata.CompanyFundamentals;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.BalanceReport;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.CashFlowReport;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.GlobalQuoteResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.IncomeReport;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.StatementsResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.SymbolSearchResponse;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;

/**
 * Domain records built from the recorded fixtures through the real mapper, for golden tests in other features
 * (metrics, scoring). Public test helper: lets those tests use real vendor data while the mapper and DTOs stay
 * package-private. Only tickers with full fundamentals fixtures ({@code AAPL}, {@code PLUG}) are supported.
 */
public final class AlphaVantageFixtures {

    public static final Instant FETCHED_AT = Instant.parse("2026-11-20T12:00:00Z");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private AlphaVantageFixtures() {
    }

    public static CompanyFundamentals fundamentals(String ticker) {
        // Only AAPL has a recorded SYMBOL_SEARCH; profile fields are irrelevant to the metrics, so use a minimal match.
        SymbolSearchResponse search = JSON.readValue("""
                {"bestMatches": [{"1. symbol": "%s", "2. name": "%s", "4. region": "United States", "8. currency": "USD"}]}
                """.formatted(ticker, ticker), SymbolSearchResponse.class);
        return AlphaVantageMapper.toFundamentals(ticker, search,
                read("global_quote-" + ticker + ".json", new TypeReference<GlobalQuoteResponse>() { }),
                read("income_statement-" + ticker + ".json", new TypeReference<StatementsResponse<IncomeReport>>() { }),
                read("balance_sheet-" + ticker + ".json", new TypeReference<StatementsResponse<BalanceReport>>() { }),
                read("cash_flow-" + ticker + ".json", new TypeReference<StatementsResponse<CashFlowReport>>() { }),
                FETCHED_AT);
    }

    private static <T> T read(String name, TypeReference<T> type) {
        try (InputStream in = AlphaVantageFixtures.class.getResourceAsStream("/fixtures/alphavantage/" + name)) {
            if (in == null) {
                throw new IllegalArgumentException("No fixture " + name);
            }
            return JSON.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
