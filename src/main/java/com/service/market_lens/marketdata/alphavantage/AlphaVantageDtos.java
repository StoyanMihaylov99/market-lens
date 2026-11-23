package com.service.market_lens.marketdata.alphavantage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Alpha Vantage response shapes, one nested record per {@code function}. Vendor types never leave this package.
 * <p>
 * Every value is kept as the raw {@code String} the vendor sends: Alpha Vantage writes numbers as strings and uses
 * {@code "None"} for missing values, even in date fields. Parsing happens in {@link AlphaVantageMapper}, where a bad
 * value becomes {@code null} instead of failing the whole response in Jackson.
 * <p>
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)}: we map only the fields we use, and new vendor fields must not
 * break us.
 */
final class AlphaVantageDtos {

    private AlphaVantageDtos() {
    }

    // ---- SYMBOL_SEARCH (profile; OVERVIEW is premium) ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SymbolSearchResponse(@Nullable List<SymbolMatch> bestMatches) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SymbolMatch(
            @JsonProperty("1. symbol") @Nullable String symbol,
            @JsonProperty("2. name") @Nullable String name,
            @JsonProperty("3. type") @Nullable String type,
            @JsonProperty("4. region") @Nullable String region,
            @JsonProperty("8. currency") @Nullable String currency) {
    }

    // ---- GLOBAL_QUOTE ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GlobalQuoteResponse(@JsonProperty("Global Quote") @Nullable GlobalQuote quote) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GlobalQuote(
            @JsonProperty("01. symbol") @Nullable String symbol,
            @JsonProperty("05. price") @Nullable String price,
            @JsonProperty("07. latest trading day") @Nullable String latestTradingDay) {
    }

    // ---- INCOME_STATEMENT / BALANCE_SHEET / CASH_FLOW: same envelope, different report type ----

    /** Lists are newest first, as the vendor sends them. Unknown tickers answer {@code {}}, so both may be null. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record StatementsResponse<T>(
            @Nullable String symbol,
            @Nullable List<T> annualReports,
            @Nullable List<T> quarterlyReports) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record IncomeReport(
            @Nullable String fiscalDateEnding,
            @Nullable String reportedCurrency,
            @Nullable String totalRevenue,
            @Nullable String grossProfit,
            @Nullable String operatingIncome,
            @Nullable String netIncome,
            @Nullable String depreciationAndAmortization) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BalanceReport(
            @Nullable String fiscalDateEnding,
            @Nullable String reportedCurrency,
            @Nullable String cashAndCashEquivalentsAtCarryingValue,
            @Nullable String shortLongTermDebtTotal,
            @Nullable String shortTermDebt,
            @Nullable String longTermDebt,
            @Nullable String totalShareholderEquity,
            @Nullable String totalAssets,
            @Nullable String totalLiabilities,
            @Nullable String commonStockSharesOutstanding) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CashFlowReport(
            @Nullable String fiscalDateEnding,
            @Nullable String reportedCurrency,
            @Nullable String operatingCashflow,
            @Nullable String capitalExpenditures) {
    }

    // ---- EARNINGS ----

    /**
     * {@code annualEarnings} is deliberately not mapped: its newest entry is a year-to-date figure ending at the latest
     * quarter, so it can't tell us the fiscal year end.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record EarningsResponse(
            @Nullable String symbol,
            @Nullable List<QuarterlyEarnings> quarterlyEarnings) {
    }

    /** {@code reportedEPS} appears to be <em>adjusted</em> EPS, so it can disagree in sign with GAAP net income. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record QuarterlyEarnings(
            @Nullable String fiscalDateEnding,
            @Nullable String reportedDate,
            @Nullable String reportedEPS,
            @Nullable String estimatedEPS) {
    }

    // ---- TIME_SERIES_DAILY (compact) and SMA, used for indices via ETF proxies ----

    /** Keyed by ISO date ({@code yyyy-MM-dd}); JSON object order is not relied on. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record TimeSeriesDailyResponse(@JsonProperty("Time Series (Daily)") @Nullable Map<String, DailyBar> series) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DailyBar(@JsonProperty("4. close") @Nullable String close) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SmaResponse(@JsonProperty("Technical Analysis: SMA") @Nullable Map<String, SmaPoint> values) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record SmaPoint(@JsonProperty("SMA") @Nullable String sma) {
    }
}
