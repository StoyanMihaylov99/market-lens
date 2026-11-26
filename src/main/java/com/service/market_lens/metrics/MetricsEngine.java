package com.service.market_lens.metrics;

import com.service.market_lens.marketdata.BalanceSheet;
import com.service.market_lens.marketdata.CashFlowStatement;
import com.service.market_lens.marketdata.CompanyFundamentals;
import com.service.market_lens.marketdata.IncomeStatement;
import com.service.market_lens.marketdata.Quote;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

import static com.service.market_lens.metrics.MetricsMath.add;
import static com.service.market_lens.metrics.MetricsMath.divideByPositive;
import static com.service.market_lens.metrics.MetricsMath.isPositive;
import static com.service.market_lens.metrics.MetricsMath.subtract;

/**
 * Turns raw fundamentals into {@link ComputedMetrics}. Pure and stateless: no Spring, no I/O, no clock, no LLM.
 * <p>
 * Flow metrics (revenue, earnings, EBITDA, cash flow) use trailing twelve months, so seasonality does not distort
 * them; stock metrics (debt, cash, equity) use the newest balance sheet. P/E is on a <b>GAAP</b> basis
 * (TTM net income ÷ shares), consistent with the statements; the vendor's adjusted EPS is only for earnings surprise.
 */
public final class MetricsEngine {

    private MetricsEngine() {
    }

    public static ComputedMetrics compute(CompanyFundamentals fundamentals) {
        Quote quote = fundamentals.quote();
        List<IncomeStatement> income = fundamentals.incomeStatements();
        List<CashFlowStatement> cashFlow = fundamentals.cashFlowStatements();
        IncomeStatement latestIncome = income.isEmpty() ? null : income.getFirst();
        CashFlowStatement latestCashFlow = cashFlow.isEmpty() ? null : cashFlow.getFirst();
        BalanceSheet latestBalance = fundamentals.balanceSheets().isEmpty() ? null : fundamentals.balanceSheets().getFirst();

        String reportingCurrency = reportingCurrency(latestIncome, latestBalance);
        boolean sameCurrency = sameCurrency(fundamentals.profile().currency(), reportingCurrency);

        // --- inputs
        BigDecimal marketCap = marketCap(quote);
        BigDecimal valuationCap = sameCurrency ? marketCap : null; // only combine price with statements in one currency
        BigDecimal shares = quote.sharesOutstanding() == null ? null : BigDecimal.valueOf(quote.sharesOutstanding());

        BigDecimal revenueTtm = incomeTtm(income, IncomeStatement::revenue);
        BigDecimal netIncomeTtm = incomeTtm(income, IncomeStatement::netIncome);
        BigDecimal ebitdaTtm = add(incomeTtm(income, IncomeStatement::operatingIncome),
                incomeTtm(income, IncomeStatement::depreciationAndAmortization));
        BigDecimal freeCashFlowTtm = add(cashFlowTtm(cashFlow, CashFlowStatement::operatingCashFlow),
                cashFlowTtm(cashFlow, CashFlowStatement::capitalExpenditure)); // capex is negative in the domain

        BigDecimal cash = latestBalance == null ? null : latestBalance.cashAndEquivalents();
        BigDecimal totalDebt = latestBalance == null ? null : latestBalance.totalDebt();
        BigDecimal equity = latestBalance == null ? null : latestBalance.totalEquity();

        BigDecimal enterpriseValue = subtract(add(valuationCap, totalDebt), cash);

        // FCF margin divides a cash-flow TTM by an income TTM: only meaningful if both end with the same quarter
        // (right after a release one vendor endpoint may already have the new quarter and the other not).
        boolean sameTtmYear = latestIncome != null && latestCashFlow != null
                && latestIncome.periodEnd().equals(latestCashFlow.periodEnd());

        return new ComputedMetrics(
                fundamentals.profile().ticker(),
                reportingCurrency,
                latestIncome == null ? null : latestIncome.periodEnd(),
                quote.price(),
                marketCap,
                latestIncome == null ? null : latestIncome.revenue(),
                revenueTtm,
                latestIncome == null ? null : latestIncome.netIncome(),
                netIncomeTtm,
                divideByPositive(netIncomeTtm, shares),
                divideByPositive(netIncomeTtm, revenueTtm),
                ebitdaTtm,
                isPositive(valuationCap) ? divideByPositive(valuationCap, netIncomeTtm) : null,
                isPositive(valuationCap) ? divideByPositive(valuationCap, revenueTtm) : null,
                isPositive(valuationCap) ? divideByPositive(valuationCap, equity) : null,
                enterpriseValue,
                isPositive(enterpriseValue) ? divideByPositive(enterpriseValue, ebitdaTtm) : null,
                totalDebt,
                subtract(totalDebt, cash),
                divideByPositive(totalDebt, equity),
                freeCashFlowTtm,
                sameTtmYear ? divideByPositive(freeCashFlowTtm, revenueTtm) : null,
                divideByPositive(freeCashFlowTtm, valuationCap));
    }

    /** Vendor market cap, else price × shares outstanding. */
    private static @Nullable BigDecimal marketCap(Quote quote) {
        if (quote.marketCap() != null) {
            return quote.marketCap();
        }
        return quote.sharesOutstanding() == null ? null
                : quote.price().multiply(BigDecimal.valueOf(quote.sharesOutstanding()));
    }

    private static @Nullable String reportingCurrency(@Nullable IncomeStatement income, @Nullable BalanceSheet balance) {
        if (income != null && income.currency() != null) {
            return income.currency();
        }
        return balance == null ? null : balance.currency();
    }

    /** Unknown currencies are assumed to match: vendors often omit them for domestic listings. */
    private static boolean sameCurrency(@Nullable String trading, @Nullable String reporting) {
        return trading == null || reporting == null || trading.equalsIgnoreCase(reporting);
    }

    private static @Nullable BigDecimal incomeTtm(List<IncomeStatement> statements,
                                                  Function<IncomeStatement, @Nullable BigDecimal> value) {
        return TrailingTwelveMonths.sum(statements, IncomeStatement::periodEnd, IncomeStatement::period, value);
    }

    private static @Nullable BigDecimal cashFlowTtm(List<CashFlowStatement> statements,
                                                    Function<CashFlowStatement, @Nullable BigDecimal> value) {
        return TrailingTwelveMonths.sum(statements, CashFlowStatement::periodEnd, CashFlowStatement::period, value);
    }
}
