package com.service.market_lens.marketdata.alphavantage;

import com.service.market_lens.marketdata.BalanceSheet;
import com.service.market_lens.marketdata.CashFlowStatement;
import com.service.market_lens.marketdata.CompanyFundamentals;
import com.service.market_lens.marketdata.CompanyProfile;
import com.service.market_lens.marketdata.DailyClose;
import com.service.market_lens.marketdata.EarningsReport;
import com.service.market_lens.marketdata.IncomeStatement;
import com.service.market_lens.marketdata.IndexSnapshot;
import com.service.market_lens.marketdata.Quote;
import com.service.market_lens.marketdata.TickerNotFoundException;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.BalanceReport;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.CashFlowReport;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.EarningsResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.GlobalQuoteResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.IncomeReport;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.QuarterlyEarnings;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.SmaResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.StatementsResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.SymbolSearchResponse;
import com.service.market_lens.marketdata.alphavantage.AlphaVantageDtos.TimeSeriesDailyResponse;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Converts Alpha Vantage DTOs into domain records. Pure functions: no I/O, no Spring, no clock.
 * <p>
 * Policy for messy vendor data: a single bad or missing <em>value</em> becomes {@code null} (the domain records are
 * nullable for exactly this reason); a row without a usable <em>date</em> is skipped; a response with no usable data
 * at all means the ticker is unknown. Lists are sorted newest first here rather than trusting vendor order.
 * <p>
 * Assumes error bodies ({@code Note}, {@code Information}, {@code Error Message}) were already rejected by the caller.
 */
final class AlphaVantageMapper {

    /** Values Alpha Vantage uses for "no data". */
    private static final Set<String> MISSING = Set.of("None", "-", "");

    /** Precision for ratios and averages: 16 significant digits, plenty for prices and percentages. */
    static final MathContext RATIO = MathContext.DECIMAL64;

    static final int FIFTY_DAYS = 50;

    /** English country name → ISO 3166 alpha-2, e.g. "United States" → "US". Built once from the JDK's list. */
    private static final Map<String, String> COUNTRY_CODES = countryCodes();

    private AlphaVantageMapper() {
    }

    // ------------------------------------------------------------------ fundamentals

    static CompanyFundamentals toFundamentals(String ticker,
                                              SymbolSearchResponse search,
                                              GlobalQuoteResponse quote,
                                              StatementsResponse<IncomeReport> income,
                                              StatementsResponse<BalanceReport> balance,
                                              StatementsResponse<CashFlowReport> cashFlow,
                                              Instant fetchedAt) {
        return new CompanyFundamentals(
                toProfile(ticker, search),
                toQuote(ticker, quote, latestSharesOutstanding(balance), fetchedAt),
                toIncomeStatements(income),
                toBalanceSheets(balance),
                toCashFlowStatements(cashFlow),
                fetchedAt);
    }

    /**
     * {@code SYMBOL_SEARCH} is a fuzzy search ("AAPL" also finds "AAPL34.SAO"), so only an exact symbol match counts.
     * Exchange, sector and industry are not available on the free tier and stay {@code null}.
     */
    static CompanyProfile toProfile(String ticker, SymbolSearchResponse response) {
        var match = Objects.requireNonNullElse(response.bestMatches(), List.<AlphaVantageDtos.SymbolMatch>of())
                .stream()
                .filter(m -> ticker.equalsIgnoreCase(text(m.symbol())))
                .findFirst()
                .orElseThrow(() -> new TickerNotFoundException(ticker));
        return new CompanyProfile(ticker, text(match.name()), null, text(match.currency()), null, null,
                countryCode(match.region()));
    }

    /**
     * Alpha Vantage's quote has no market cap or share count; shares come from the latest balance sheet and market
     * cap is computed as price × shares. The quote has only a trading <em>date</em>, so {@code asOf} is midnight UTC
     * of that day ({@code fallbackAsOf} if the date is missing).
     */
    static Quote toQuote(String ticker, GlobalQuoteResponse response, @Nullable Long sharesOutstanding,
                         Instant fallbackAsOf) {
        var quote = response.quote();
        BigDecimal price = quote == null ? null : decimal(quote.price());
        if (price == null) {
            throw new TickerNotFoundException(ticker); // unknown tickers answer {"Global Quote": {}}
        }
        LocalDate day = date(quote.latestTradingDay());
        Instant asOf = day == null ? fallbackAsOf : day.atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal marketCap = sharesOutstanding == null ? null : price.multiply(BigDecimal.valueOf(sharesOutstanding));
        return new Quote(ticker, price, marketCap, sharesOutstanding, asOf);
    }

    /** Newest quarterly {@code commonStockSharesOutstanding} that has a value. */
    static @Nullable Long latestSharesOutstanding(StatementsResponse<BalanceReport> response) {
        return sortedQuarterly(response, BalanceReport::fiscalDateEnding).stream()
                .map(r -> wholeNumber(r.commonStockSharesOutstanding()))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    /** Quarterly statements only; annual reports are used just to learn the fiscal year end. */
    static List<IncomeStatement> toIncomeStatements(StatementsResponse<IncomeReport> response) {
        var calendar = calendar(response.annualReports(), IncomeReport::fiscalDateEnding);
        return sortedQuarterly(response, IncomeReport::fiscalDateEnding).stream()
                .map(r -> {
                    LocalDate end = Objects.requireNonNull(date(r.fiscalDateEnding()));
                    return new IncomeStatement(end, calendar.fiscalYear(end), calendar.quarter(end),
                            text(r.reportedCurrency()),
                            decimal(r.totalRevenue()),
                            decimal(r.grossProfit()),
                            decimal(r.operatingIncome()),
                            decimal(r.netIncome()),
                            decimal(r.depreciationAndAmortization()),
                            null,   // INCOME_STATEMENT has no EPS
                            null);  // ... and no diluted share count
                })
                .toList();
    }

    /**
     * Total debt prefers the vendor's {@code shortLongTermDebtTotal}; if missing, short-term + long-term debt
     * (either alone when only one is present).
     */
    static List<BalanceSheet> toBalanceSheets(StatementsResponse<BalanceReport> response) {
        var calendar = calendar(response.annualReports(), BalanceReport::fiscalDateEnding);
        return sortedQuarterly(response, BalanceReport::fiscalDateEnding).stream()
                .map(r -> {
                    LocalDate end = Objects.requireNonNull(date(r.fiscalDateEnding()));
                    return new BalanceSheet(end, calendar.fiscalYear(end), calendar.quarter(end),
                            text(r.reportedCurrency()),
                            decimal(r.cashAndCashEquivalentsAtCarryingValue()),
                            totalDebt(r),
                            decimal(r.totalShareholderEquity()),
                            decimal(r.totalAssets()),
                            decimal(r.totalLiabilities()));
                })
                .toList();
    }

    /** Alpha Vantage reports capex as a positive amount; the domain wants cash going out as a negative number. */
    static List<CashFlowStatement> toCashFlowStatements(StatementsResponse<CashFlowReport> response) {
        var calendar = calendar(response.annualReports(), CashFlowReport::fiscalDateEnding);
        return sortedQuarterly(response, CashFlowReport::fiscalDateEnding).stream()
                .map(r -> {
                    LocalDate end = Objects.requireNonNull(date(r.fiscalDateEnding()));
                    BigDecimal capex = decimal(r.capitalExpenditures());
                    return new CashFlowStatement(end, calendar.fiscalYear(end), calendar.quarter(end),
                            text(r.reportedCurrency()),
                            decimal(r.operatingCashflow()),
                            capex == null ? null : capex.abs().negate());
                })
                .toList();
    }

    // ------------------------------------------------------------------ earnings

    /**
     * The {@code count} most recent <b>already reported</b> quarterly earnings releases (rows without a reported EPS
     * are scheduled releases and are excluded), newest first. {@code EARNINGS} has EPS but no
     * revenue, so revenue and net income are joined from the quarterly income statement with the same period end.
     * There is no revenue estimate on the free tier.
     * <p>
     * Note: {@code reportedEPS} looks like <em>adjusted</em> EPS while net income is GAAP, so the two can disagree in
     * sign (PLUG, Q3 2026).
     */
    static List<EarningsReport> toEarnings(String ticker, EarningsResponse earnings,
                                           StatementsResponse<IncomeReport> income, int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive, was " + count);
        }
        // Not EARNINGS.annualEarnings: its newest entry is a year-to-date figure ending at the latest quarter
        // (PLUG: "2026-09-30" although its fiscal year ends in December). Income annual reports are completed years.
        var calendar = calendar(income.annualReports(), IncomeReport::fiscalDateEnding);

        Map<LocalDate, IncomeReport> incomeByPeriodEnd = new HashMap<>();
        for (IncomeReport r : Objects.requireNonNullElse(income.quarterlyReports(), List.<IncomeReport>of())) {
            LocalDate end = date(r.fiscalDateEnding());
            if (end != null) {
                incomeByPeriodEnd.putIfAbsent(end, r);
            }
        }

        return Objects.requireNonNullElse(earnings.quarterlyEarnings(), List.<QuarterlyEarnings>of()).stream()
                .filter(e -> date(e.reportedDate()) != null)
                .filter(e -> decimal(e.reportedEPS()) != null)   // scheduled, not yet reported → excluded (port contract)
                .sorted(Comparator.comparing((QuarterlyEarnings e) -> date(e.reportedDate())).reversed())
                .limit(count)
                .map(e -> {
                    LocalDate end = date(e.fiscalDateEnding());
                    IncomeReport matching = end == null ? null : incomeByPeriodEnd.get(end);
                    return new EarningsReport(
                            ticker,
                            Objects.requireNonNull(date(e.reportedDate())),
                            end,
                            end == null ? null : calendar.fiscalYear(end),
                            end == null ? null : calendar.quarter(end),
                            decimal(e.reportedEPS()),
                            decimal(e.estimatedEPS()),
                            matching == null ? null : decimal(matching.totalRevenue()),
                            null,
                            matching == null ? null : decimal(matching.netIncome()));
                })
                .toList();
    }

    // ------------------------------------------------------------------ indices (ETF proxies)

    /**
     * Snapshot from up to ~100 daily closes ({@code compact}). Change % is the last close versus the previous one,
     * as a decimal fraction. The 50-day average is computed here; the 200-day average needs more history than the
     * free tier returns, so it comes from the separate {@code SMA} call (optional).
     */
    static IndexSnapshot toIndexSnapshot(String symbol, @Nullable String name, TimeSeriesDailyResponse series,
                                         @Nullable SmaResponse sma200) {
        List<DailyClose> history = Objects.requireNonNullElse(series.series(), Map.<String, AlphaVantageDtos.DailyBar>of())
                .entrySet().stream()
                .map(e -> {
                    LocalDate day = date(e.getKey());
                    BigDecimal close = e.getValue() == null ? null : decimal(e.getValue().close());
                    return day == null || close == null ? null : new DailyClose(day, close);
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(DailyClose::date).reversed())
                .toList();
        if (history.isEmpty()) {
            throw new TickerNotFoundException(symbol);
        }

        BigDecimal price = history.getFirst().close();
        BigDecimal changePercent = null;
        if (history.size() >= 2 && history.get(1).close().signum() != 0) {
            BigDecimal previous = history.get(1).close();
            changePercent = price.subtract(previous).divide(previous, RATIO);
        }
        BigDecimal fiftyDayAverage = history.size() < FIFTY_DAYS ? null
                : history.subList(0, FIFTY_DAYS).stream()
                        .map(DailyClose::close)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(FIFTY_DAYS), RATIO);

        return new IndexSnapshot(symbol, name, price, changePercent, fiftyDayAverage, latestSma(sma200), history);
    }

    private static @Nullable BigDecimal latestSma(@Nullable SmaResponse response) {
        if (response == null || response.values() == null) {
            return null;
        }
        return response.values().entrySet().stream()
                .filter(e -> date(e.getKey()) != null && e.getValue() != null && decimal(e.getValue().sma()) != null)
                .max(Comparator.comparing(e -> date(e.getKey())))
                .map(e -> decimal(e.getValue().sma()))
                .orElse(null);
    }

    // ------------------------------------------------------------------ value parsing

    /** Trimmed text, or {@code null} for Alpha Vantage's "no data" markers. */
    static @Nullable String text(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        return MISSING.contains(trimmed) ? null : trimmed;
    }

    /** {@code "-0.12"} → {@code -0.12}; {@code "None"}, {@code "-"}, blank or garbage → {@code null}. */
    static @Nullable BigDecimal decimal(@Nullable String raw) {
        String value = text(raw);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Share counts: whole numbers only; anything with a fraction or out of {@code long} range → {@code null}. */
    static @Nullable Long wholeNumber(@Nullable String raw) {
        BigDecimal value = decimal(raw);
        if (value == null) {
            return null;
        }
        try {
            return value.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException e) {
            return null;
        }
    }

    /** ISO date ({@code 2026-09-30}) or {@code null}. */
    static @Nullable LocalDate date(@Nullable String raw) {
        String value = text(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Alpha Vantage regions are English country names ("United States") or "City/Country" ("Toronto/Canada").
     * Unknown regions → {@code null}.
     */
    static @Nullable String countryCode(@Nullable String region) {
        String value = text(region);
        if (value == null) {
            return null;
        }
        for (String part : value.split("/")) {
            String code = COUNTRY_CODES.get(part.strip().toLowerCase(Locale.ROOT));
            if (code != null) {
                return code;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    private static @Nullable BigDecimal totalDebt(BalanceReport r) {
        BigDecimal total = decimal(r.shortLongTermDebtTotal());
        if (total != null) {
            return total;
        }
        BigDecimal shortTerm = decimal(r.shortTermDebt());
        BigDecimal longTerm = decimal(r.longTermDebt());
        if (shortTerm == null) {
            return longTerm;
        }
        return longTerm == null ? shortTerm : shortTerm.add(longTerm);
    }

    /** Quarterly reports with a parseable period end, newest first. */
    private static <T> List<T> sortedQuarterly(StatementsResponse<T> response, Function<T, @Nullable String> periodEnd) {
        return Objects.requireNonNullElse(response.quarterlyReports(), List.<T>of()).stream()
                .filter(r -> date(periodEnd.apply(r)) != null)
                .sorted(Comparator.comparing((T r) -> date(periodEnd.apply(r))).reversed())
                .toList();
    }

    /** Fiscal calendar from the newest annual period end; calendar year when there are no annual reports. */
    private static <T> FiscalCalendar calendar(@Nullable List<T> annual, Function<T, @Nullable String> periodEnd) {
        LocalDate latest = Objects.requireNonNullElse(annual, List.<T>of()).stream()
                .map(r -> date(periodEnd.apply(r)))
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return FiscalCalendar.fromLatestAnnualEnd(latest);
    }

    private static Map<String, String> countryCodes() {
        Map<String, String> codes = new HashMap<>();
        for (String code : Locale.getISOCountries()) {
            codes.put(Locale.of("", code).getDisplayCountry(Locale.ENGLISH).toLowerCase(Locale.ROOT), code);
        }
        return Map.copyOf(codes);
    }
}
