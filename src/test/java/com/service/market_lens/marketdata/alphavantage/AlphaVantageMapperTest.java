package com.service.market_lens.marketdata.alphavantage;

import com.service.market_lens.marketdata.EarningsReport;
import com.service.market_lens.marketdata.FiscalPeriod;
import com.service.market_lens.marketdata.TickerNotFoundException;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.BalanceReport;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.CashFlowReport;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.DailyBar;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.EarningsResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.GlobalQuoteResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.IncomeReport;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.QuarterlyEarnings;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.SmaResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.StatementsResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.SymbolSearchResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.TimeSeriesDailyResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.service.market_lens.marketdata.alphavantage.AlphaVantageMapper.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Maps the real recorded responses in {@code fixtures/alphavantage} (see its README). Expected numbers were read
 * from the fixture files, not computed with the code under test.
 */
class AlphaVantageMapperTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Instant FETCHED_AT = Instant.parse("2026-11-20T12:00:00Z");

    /** Generic envelopes (statements) need a TypeReference so Jackson knows the report type. */
    private static <T> T fixture(String name, TypeReference<T> type) {
        try (InputStream in = open(name)) {
            return JSON.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> T fixture(String name, Class<T> type) {
        try (InputStream in = open(name)) {
            return JSON.readValue(in, type);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static InputStream open(String name) {
        InputStream in = AlphaVantageMapperTest.class.getResourceAsStream("/fixtures/alphavantage/" + name);
        assertThat(in).as("fixture %s", name).isNotNull();
        return in;
    }

    private static StatementsResponse<IncomeReport> income(String ticker) {
        return fixture("income_statement-" + ticker + ".json", new TypeReference<>() { });
    }

    private static StatementsResponse<BalanceReport> balance(String ticker) {
        return fixture("balance_sheet-" + ticker + ".json", new TypeReference<>() { });
    }

    private static StatementsResponse<CashFlowReport> cashFlow(String ticker) {
        return fixture("cash_flow-" + ticker + ".json", new TypeReference<>() { });
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Nested
    class Fundamentals {

        @Test
        void mapsAppleEndToEnd() {
            var f = toFundamentals("AAPL",
                    fixture("symbol_search-AAPL.json", SymbolSearchResponse.class),
                    fixture("global_quote-AAPL.json", GlobalQuoteResponse.class),
                    income("AAPL"), balance("AAPL"), cashFlow("AAPL"), FETCHED_AT);

            assertThat(f.profile().name()).isEqualTo("Apple Inc");
            assertThat(f.quote().price()).isEqualByComparingTo("349.21");
            assertThat(f.incomeStatements()).hasSize(8);
            assertThat(f.balanceSheets()).hasSize(8);
            assertThat(f.cashFlowStatements()).hasSize(8);
            assertThat(f.fetchedAt()).isEqualTo(FETCHED_AT);
        }

        @Test
        void profileTakesExactMatchOnly() {
            var profile = toProfile("AAPL", fixture("symbol_search-AAPL.json", SymbolSearchResponse.class));

            assertThat(profile.ticker()).isEqualTo("AAPL");
            assertThat(profile.name()).isEqualTo("Apple Inc");
            assertThat(profile.currency()).isEqualTo("USD");
            assertThat(profile.country()).isEqualTo("US");
            assertThat(profile.exchange()).isNull();   // not on the free tier
            assertThat(profile.sector()).isNull();
            assertThat(profile.industry()).isNull();
        }

        @Test
        void profileWithoutExactMatchIsUnknownTicker() {
            var search = fixture("symbol_search-AAPL.json", SymbolSearchResponse.class);

            assertThatThrownBy(() -> toProfile("AAP", search)).isInstanceOf(TickerNotFoundException.class);
            assertThatThrownBy(() -> toProfile("AAPL", new SymbolSearchResponse(null)))
                    .isInstanceOf(TickerNotFoundException.class);
        }

        @Test
        void quoteComputesMarketCapFromBalanceSheetShares() {
            Long shares = latestSharesOutstanding(balance("AAPL"));
            var quote = toQuote("AAPL", fixture("global_quote-AAPL.json", GlobalQuoteResponse.class), shares, FETCHED_AT);

            assertThat(shares).isEqualTo(14_630_318_000L);
            assertThat(quote.sharesOutstanding()).isEqualTo(14_630_318_000L);
            assertThat(quote.marketCap()).isEqualByComparingTo("5109053348780");
            assertThat(quote.asOf()).isEqualTo(Instant.parse("2026-11-19T00:00:00Z"));
        }

        @Test
        void quoteWithoutSharesHasNoMarketCap() {
            var quote = toQuote("AAPL", fixture("global_quote-AAPL.json", GlobalQuoteResponse.class), null, FETCHED_AT);

            assertThat(quote.marketCap()).isNull();
        }

        @Test
        void emptyGlobalQuoteIsUnknownTicker() {
            var empty = fixture("global_quote-UNKNOWNXYZ.json", GlobalQuoteResponse.class);

            assertThatThrownBy(() -> toQuote("UNKNOWNXYZ", empty, null, FETCHED_AT))
                    .isInstanceOf(TickerNotFoundException.class);
        }

        @Test
        void incomeStatementUsesFiscalYearEndingInSeptember() {
            var statements = toIncomeStatements(income("AAPL"));

            var latest = statements.getFirst();
            assertThat(latest.periodEnd()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(latest.period()).isEqualTo(FiscalPeriod.Q4);
            assertThat(latest.fiscalYear()).isEqualTo(2026);
            assertThat(latest.currency()).isEqualTo("USD");
            assertThat(latest.revenue()).isEqualByComparingTo("114477000000");
            assertThat(latest.grossProfit()).isEqualByComparingTo("54820000000");
            assertThat(latest.operatingIncome()).isEqualByComparingTo("35631000000");
            assertThat(latest.netIncome()).isEqualByComparingTo("30711000000");
            assertThat(latest.depreciationAndAmortization()).isEqualByComparingTo("3450000000");
            assertThat(latest.epsDiluted()).isNull();               // not in INCOME_STATEMENT
            assertThat(latest.weightedAvgSharesDiluted()).isNull();

            var december = statements.get(3);
            assertThat(december.periodEnd()).isEqualTo(LocalDate.of(2025, 12, 31));
            assertThat(december.period()).isEqualTo(FiscalPeriod.Q1);
            assertThat(december.fiscalYear()).isEqualTo(2026);
        }

        @Test
        void incomeStatementsAreNewestFirst() {
            var ends = toIncomeStatements(income("PLUG")).stream().map(s -> s.periodEnd()).toList();

            assertThat(ends).isSortedAccordingTo((a, b) -> b.compareTo(a));
        }

        @Test
        void negativeValuesSurvive() {
            var plug = toIncomeStatements(income("PLUG")).getFirst();

            assertThat(plug.period()).isEqualTo(FiscalPeriod.Q3);    // December fiscal year end
            assertThat(plug.grossProfit()).isEqualByComparingTo("-5358000");
        }

        @Test
        void balanceSheetPrefersVendorDebtTotal() {
            var latest = toBalanceSheets(balance("AAPL")).getFirst();

            assertThat(latest.totalDebt()).isEqualByComparingTo("80111000000");
            assertThat(latest.totalEquity()).isEqualByComparingTo("112093000000");
            assertThat(latest.cashAndEquivalents()).isEqualByComparingTo("36305000000");
            assertThat(latest.totalAssets()).isEqualByComparingTo("404548000000");
            assertThat(latest.totalLiabilities()).isEqualByComparingTo("292455000000");
        }

        @Test
        void balanceSheetSumsDebtWhenTotalIsMissing() {
            var both = report("None", "100", "250");
            var onlyLong = report("None", "None", "250");
            var none = report("None", "None", "None");

            var sheets = toBalanceSheets(new StatementsResponse<>("X", null, List.of(both)));
            assertThat(sheets.getFirst().totalDebt()).isEqualByComparingTo("350");
            assertThat(toBalanceSheets(new StatementsResponse<>("X", null, List.of(onlyLong))).getFirst().totalDebt())
                    .isEqualByComparingTo("250");
            assertThat(toBalanceSheets(new StatementsResponse<>("X", null, List.of(none))).getFirst().totalDebt())
                    .isNull();
        }

        private static BalanceReport report(String total, String shortTerm, String longTerm) {
            return new BalanceReport("2026-09-30", "USD", "1", total, shortTerm, longTerm, "1", "1", "1", "1");
        }

        @Test
        void capexBecomesNegative() {
            var plug = toCashFlowStatements(cashFlow("PLUG")).getFirst();

            assertThat(plug.operatingCashFlow()).isEqualByComparingTo("-76420000");
            assertThat(plug.capitalExpenditure()).isEqualByComparingTo("-14802000"); // vendor: "14802000"
        }

        @Test
        void unknownTickerStatementsAreEmpty() {
            StatementsResponse<IncomeReport> empty =
                    fixture("income_statement-UNKNOWNXYZ.json", new TypeReference<>() { });

            assertThat(toIncomeStatements(empty)).isEmpty();
            assertThat(latestSharesOutstanding(new StatementsResponse<>(null, null, null))).isNull();
        }

        @Test
        void rowsWithoutUsableDateAreSkipped() {
            var noDate = new IncomeReport("None", "USD", "1", "1", "1", "1", "1");
            var ok = new IncomeReport("2026-06-30", "USD", "1", "1", "1", "1", "1");

            assertThat(toIncomeStatements(new StatementsResponse<>("X", null, List.of(noDate, ok))))
                    .singleElement()
                    .satisfies(s -> assertThat(s.periodEnd()).isEqualTo(LocalDate.of(2026, 6, 30)));
        }
    }

    @Nested
    class Earnings {

        private final EarningsResponse apple = fixture("earnings-AAPL.json", EarningsResponse.class);

        @Test
        void joinsRevenueAndNetIncomeByPeriodEnd() {
            List<EarningsReport> reports = toEarnings("AAPL", apple, income("AAPL"), 2);

            assertThat(reports).hasSize(2);
            var latest = reports.getFirst();
            assertThat(latest.ticker()).isEqualTo("AAPL");
            assertThat(latest.reportDate()).isEqualTo(LocalDate.of(2026, 11, 2));
            assertThat(latest.periodEnd()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(latest.fiscalYear()).isEqualTo(2026);
            assertThat(latest.period()).isEqualTo(FiscalPeriod.Q4);
            assertThat(latest.epsActual()).isEqualByComparingTo("2.05");
            assertThat(latest.epsEstimate()).isEqualByComparingTo("1.98");
            assertThat(latest.revenueActual()).isEqualByComparingTo("114477000000");
            assertThat(latest.netIncome()).isEqualByComparingTo("30711000000");
            assertThat(latest.revenueEstimate()).isNull();          // not on the free tier

            assertThat(reports.get(1).reportDate()).isEqualTo(LocalDate.of(2026, 7, 30));
        }

        @Test
        void fiscalQuarterComesFromCompletedYearsNotYearToDateEntry() {
            // earnings-PLUG.json's newest annualEarnings entry is year-to-date "2026-09-30"; PLUG's year ends in December
            var plug = toEarnings("PLUG", fixture("earnings-PLUG.json", EarningsResponse.class), income("PLUG"), 1)
                    .getFirst();

            assertThat(plug.periodEnd()).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(plug.period()).isEqualTo(FiscalPeriod.Q3);
            assertThat(plug.fiscalYear()).isEqualTo(2026);
        }

        @Test
        void adjustedEpsCanDisagreeWithGaapNetIncome() {
            var plug = toEarnings("PLUG", fixture("earnings-PLUG.json", EarningsResponse.class), income("PLUG"), 1)
                    .getFirst();

            assertThat(plug.epsActual()).isEqualByComparingTo("-0.12");
            assertThat(plug.netIncome()).isEqualByComparingTo("99766000");
        }

        @Test
        void missingEstimateAndUnmatchedPeriodBecomeNull() {
            var noEstimate = new QuarterlyEarnings("2019-03-31", "2019-05-01", "0.50", "None");
            var response = new EarningsResponse("X", List.of(noEstimate));

            var report = toEarnings("X", response, new StatementsResponse<>("X", null, null), 4).getFirst();

            assertThat(report.epsActual()).isEqualByComparingTo("0.50");
            assertThat(report.epsEstimate()).isNull();
            assertThat(report.revenueActual()).isNull();
            assertThat(report.netIncome()).isNull();
            assertThat(report.period()).isEqualTo(FiscalPeriod.Q1);  // calendar-year fallback
        }

        @Test
        void releasesWithoutReportDateAreSkippedAndCountIsApplied() {
            var noDate = new QuarterlyEarnings("2026-09-30", "None", "1", "1");
            var older = new QuarterlyEarnings("2026-03-31", "2026-05-01", "1", "1");
            var newer = new QuarterlyEarnings("2026-06-30", "2026-08-01", "1", "1");
            var response = new EarningsResponse("X", List.of(noDate, older, newer));

            var reports = toEarnings("X", response, new StatementsResponse<>("X", null, null), 5);

            assertThat(reports).extracting(EarningsReport::reportDate)
                    .containsExactly(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 5, 1));
        }

        @Test
        void scheduledReleasesWithoutReportedEpsAreExcluded() {
            var scheduled = new QuarterlyEarnings("2026-12-31", "2027-02-01", "None", "2.10");
            var reported = new QuarterlyEarnings("2026-09-30", "2026-11-02", "2.05", "1.98");
            var response = new EarningsResponse("X", List.of(scheduled, reported));

            assertThat(toEarnings("X", response, new StatementsResponse<>("X", null, null), 4))
                    .extracting(EarningsReport::reportDate)
                    .containsExactly(LocalDate.of(2026, 11, 2));
        }

        @Test
        void countMustBePositive() {
            assertThatThrownBy(() -> toEarnings("AAPL", apple, income("AAPL"), 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Indices {

        @Test
        void mapsSpyWithFiftyDayAverageAndSma() {
            var snapshot = toIndexSnapshot("SPY", "S&P 500 (SPY)",
                    fixture("time_series_daily-SPY.json", TimeSeriesDailyResponse.class),
                    fixture("sma200-SPY.json", SmaResponse.class));

            assertThat(snapshot.symbol()).isEqualTo("SPY");
            assertThat(snapshot.name()).isEqualTo("S&P 500 (SPY)");
            assertThat(snapshot.price()).isEqualByComparingTo("715.87");
            assertThat(snapshot.changePercent()).isCloseTo(bd("-0.01962476033963298"), within(bd("1e-12")));
            assertThat(snapshot.fiftyDayAverage()).isEqualByComparingTo("761.6694");
            assertThat(snapshot.twoHundredDayAverage()).isEqualByComparingTo("730.2056");
            assertThat(snapshot.history()).hasSize(100);
            assertThat(snapshot.history().getFirst().date()).isEqualTo(LocalDate.of(2026, 11, 19));
            assertThat(snapshot.history().getLast().date()).isEqualTo(LocalDate.of(2026, 7, 1));
        }

        @Test
        void shortHistoryHasNoFiftyDayAverageAndSmaIsOptional() {
            var series = new TimeSeriesDailyResponse(Map.of(
                    "2026-11-19", new DailyBar("110"),
                    "2026-11-18", new DailyBar("100")));

            var snapshot = toIndexSnapshot("SPY", null, series, null);

            assertThat(snapshot.price()).isEqualByComparingTo("110");
            assertThat(snapshot.changePercent()).isEqualByComparingTo("0.1");
            assertThat(snapshot.fiftyDayAverage()).isNull();
            assertThat(snapshot.twoHundredDayAverage()).isNull();
        }

        @Test
        void emptySeriesIsUnknownSymbol() {
            assertThatThrownBy(() -> toIndexSnapshot("NOPE", null, new TimeSeriesDailyResponse(null), null))
                    .isInstanceOf(TickerNotFoundException.class);
        }
    }

    @Nested
    class Values {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"None", "-", "  ", "abc", "12,5"})
        void missingOrGarbageDecimalIsNull(String raw) {
            assertThat(decimal(raw)).isNull();
        }

        @Test
        void parsesDecimalsAndWholeNumbers() {
            assertThat(decimal(" -0.12 ")).isEqualByComparingTo("-0.12");
            assertThat(wholeNumber("14630318000")).isEqualTo(14_630_318_000L);
            assertThat(wholeNumber("12.5")).isNull();
            assertThat(wholeNumber("None")).isNull();
        }

        @Test
        void parsesDatesLeniently() {
            assertThat(date("2026-09-30")).isEqualTo(LocalDate.of(2026, 9, 30));
            assertThat(date("None")).isNull();
            assertThat(date("30.09.2026")).isNull();
        }

        @Test
        void mapsRegionsToCountryCodes() {
            assertThat(countryCode("United States")).isEqualTo("US");
            assertThat(countryCode("United Kingdom")).isEqualTo("GB");
            assertThat(countryCode("Toronto/Canada")).isEqualTo("CA");
            assertThat(countryCode("Atlantis")).isNull();
            assertThat(countryCode(null)).isNull();
        }
    }
}
