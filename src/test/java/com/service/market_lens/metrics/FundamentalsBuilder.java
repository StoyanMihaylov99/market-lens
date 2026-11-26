package com.service.market_lens.metrics;

import com.service.market_lens.marketdata.BalanceSheet;
import com.service.market_lens.marketdata.CashFlowStatement;
import com.service.market_lens.marketdata.CompanyFundamentals;
import com.service.market_lens.marketdata.CompanyProfile;
import com.service.market_lens.marketdata.FiscalPeriod;
import com.service.market_lens.marketdata.IncomeStatement;
import com.service.market_lens.marketdata.Quote;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link CompanyFundamentals} with round numbers so expected metrics are easy to calculate by hand.
 * Every quarter gets the same flow values, so TTM = 4 × the quarterly value. Defaults (TTM):
 * revenue 100bn, net income 10bn, operating income 12bn, D&amp;A 2bn (EBITDA 14bn), OCF 12bn, capex −4bn (FCF 8bn);
 * latest balance sheet: cash 20bn, debt 50bn, equity 40bn; price 100 × 1bn shares (market cap 100bn); all USD.
 */
final class FundamentalsBuilder {

    static final LocalDate LATEST = LocalDate.of(2026, 9, 30);
    static final Instant AS_OF = Instant.parse("2026-11-20T00:00:00Z");

    private BigDecimal price = new BigDecimal("100");
    private @Nullable BigDecimal marketCap;
    private @Nullable Long shares = 1_000_000_000L;
    private @Nullable String tradingCurrency = "USD";
    private @Nullable String reportingCurrency = "USD";
    private int quarters = 4;
    private @Nullable BigDecimal revenue = bn("25");
    private @Nullable BigDecimal netIncome = bn("2.5");
    private @Nullable BigDecimal operatingIncome = bn("3");
    private @Nullable BigDecimal depreciation = bn("0.5");
    private @Nullable BigDecimal operatingCashFlow = bn("3");
    private @Nullable BigDecimal capex = bn("-1");
    private @Nullable BigDecimal cash = bn("20");
    private @Nullable BigDecimal debt = bn("50");
    private @Nullable BigDecimal equity = bn("40");

    /** Billions: {@code bn("2.5")} = 2,500,000,000. */
    static BigDecimal bn(String billions) {
        return new BigDecimal(billions).movePointRight(9);
    }

    FundamentalsBuilder price(String value) { price = new BigDecimal(value); return this; }
    FundamentalsBuilder marketCap(@Nullable BigDecimal value) { marketCap = value; return this; }
    FundamentalsBuilder shares(@Nullable Long value) { shares = value; return this; }
    FundamentalsBuilder tradingCurrency(@Nullable String value) { tradingCurrency = value; return this; }
    FundamentalsBuilder reportingCurrency(@Nullable String value) { reportingCurrency = value; return this; }
    FundamentalsBuilder quarters(int value) { quarters = value; return this; }
    /** Per quarter. */
    FundamentalsBuilder revenue(@Nullable BigDecimal value) { revenue = value; return this; }
    /** Per quarter. */
    FundamentalsBuilder netIncome(@Nullable BigDecimal value) { netIncome = value; return this; }
    /** Per quarter. */
    FundamentalsBuilder operatingIncome(@Nullable BigDecimal value) { operatingIncome = value; return this; }
    /** Per quarter. */
    FundamentalsBuilder depreciation(@Nullable BigDecimal value) { depreciation = value; return this; }
    /** Per quarter. */
    FundamentalsBuilder operatingCashFlow(@Nullable BigDecimal value) { operatingCashFlow = value; return this; }
    /** Per quarter, negative = cash out. */
    FundamentalsBuilder capex(@Nullable BigDecimal value) { capex = value; return this; }
    FundamentalsBuilder cash(@Nullable BigDecimal value) { cash = value; return this; }
    FundamentalsBuilder debt(@Nullable BigDecimal value) { debt = value; return this; }
    FundamentalsBuilder equity(@Nullable BigDecimal value) { equity = value; return this; }

    CompanyFundamentals build() {
        List<IncomeStatement> income = new ArrayList<>();
        List<BalanceSheet> balance = new ArrayList<>();
        List<CashFlowStatement> cashFlow = new ArrayList<>();
        for (int i = 0; i < quarters; i++) {
            LocalDate end = LATEST.minusMonths(3L * i).withDayOfMonth(1).plusMonths(1).minusDays(1); // month end
            FiscalPeriod period = FiscalPeriod.values()[3 - (i % 4)];
            income.add(new IncomeStatement(end, 2026, period, reportingCurrency, revenue, null, operatingIncome,
                    netIncome, depreciation, null, null));
            balance.add(new BalanceSheet(end, 2026, period, reportingCurrency, cash, debt, equity, null, null));
            cashFlow.add(new CashFlowStatement(end, 2026, period, reportingCurrency, operatingCashFlow, capex));
        }
        return new CompanyFundamentals(
                new CompanyProfile("TEST", "Test Corp", null, tradingCurrency, null, null, "US"),
                new Quote("TEST", price, marketCap, shares, AS_OF),
                income, balance, cashFlow, AS_OF);
    }
}
