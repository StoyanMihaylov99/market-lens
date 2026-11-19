package com.service.market_lens.marketdata;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Ready-made, realistic domain records for tests. Values are loosely based on an Apple quarter.
 * <p>
 * Public so later phases (metrics, scoring, analysis) can reuse it. Use the record's own constructor
 * when a test needs a specific value (e.g. a null estimate or a negative net income).
 */
public final class MarketDataTestData {

    public static final String TICKER = "AAPL";
    public static final LocalDate LATEST_PERIOD_END = LocalDate.of(2026, 6, 27);
    public static final LocalDate PREVIOUS_PERIOD_END = LocalDate.of(2026, 3, 28);
    public static final Instant FETCHED_AT = Instant.parse("2026-11-19T10:00:00Z");

    private MarketDataTestData() {
    }

    public static CompanyProfile profile() {
        return new CompanyProfile(TICKER, "Apple Inc.", "NASDAQ", "USD", "Technology",
                "Consumer Electronics", "US");
    }

    public static Quote quote() {
        return new Quote(TICKER, new BigDecimal("230.50"), new BigDecimal("3420000000000"),
                14_840_000_000L, FETCHED_AT);
    }

    public static IncomeStatement incomeStatement(LocalDate periodEnd, FiscalPeriod period) {
        return new IncomeStatement(periodEnd, 2026, period, "USD",
                new BigDecimal("94036000000"), new BigDecimal("43718000000"),
                new BigDecimal("28202000000"), new BigDecimal("23434000000"),
                new BigDecimal("3000000000"), new BigDecimal("1.57"), 14_948_500_000L);
    }

    public static BalanceSheet balanceSheet(LocalDate periodEnd, FiscalPeriod period) {
        return new BalanceSheet(periodEnd, 2026, period, "USD",
                new BigDecimal("36269000000"), new BigDecimal("101698000000"),
                new BigDecimal("65830000000"), new BigDecimal("331495000000"),
                new BigDecimal("265665000000"));
    }

    public static CashFlowStatement cashFlowStatement(LocalDate periodEnd, FiscalPeriod period) {
        return new CashFlowStatement(periodEnd, 2026, period, "USD",
                new BigDecimal("27867000000"), new BigDecimal("-3462000000"));
    }

    /** Two quarters, newest first. */
    public static CompanyFundamentals fundamentals() {
        return new CompanyFundamentals(profile(), quote(),
                List.of(incomeStatement(LATEST_PERIOD_END, FiscalPeriod.Q3),
                        incomeStatement(PREVIOUS_PERIOD_END, FiscalPeriod.Q2)),
                List.of(balanceSheet(LATEST_PERIOD_END, FiscalPeriod.Q3),
                        balanceSheet(PREVIOUS_PERIOD_END, FiscalPeriod.Q2)),
                List.of(cashFlowStatement(LATEST_PERIOD_END, FiscalPeriod.Q3),
                        cashFlowStatement(PREVIOUS_PERIOD_END, FiscalPeriod.Q2)),
                FETCHED_AT);
    }

    public static EarningsReport earningsReport() {
        return new EarningsReport(TICKER, LocalDate.of(2026, 7, 31), LATEST_PERIOD_END, 2026, FiscalPeriod.Q3,
                new BigDecimal("1.57"), new BigDecimal("1.43"),
                new BigDecimal("94036000000"), new BigDecimal("89350000000"),
                new BigDecimal("23434000000"));
    }

    /** Three closes, newest first. */
    public static IndexSnapshot indexSnapshot() {
        return new IndexSnapshot("^GSPC", "S&P 500", new BigDecimal("6650.10"), new BigDecimal("0.0123"),
                new BigDecimal("6500.00"), new BigDecimal("6200.00"),
                List.of(new DailyClose(LocalDate.of(2026, 11, 18), new BigDecimal("6650.10")),
                        new DailyClose(LocalDate.of(2026, 11, 17), new BigDecimal("6569.30")),
                        new DailyClose(LocalDate.of(2026, 11, 16), new BigDecimal("6601.75"))));
    }
}
