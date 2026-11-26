package com.service.market_lens.metrics;

import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Every number the application derives from {@link com.service.market_lens.marketdata.CompanyFundamentals}.
 * Computed by {@link MetricsEngine}; the LLM only ever sees these finished values.
 * <p>
 * <b>Units.</b> Money in the reporting currency, exact. Ratios (P/E, D/E, ...) and fractions (margins, yields) have
 * 4 decimal places; fractions are decimal ({@code 0.2760} = 27.60%). {@code TTM} = sum of the four newest quarters.
 * <p>
 * <b>Nulls.</b> Every metric is nullable: a missing input, an incomplete TTM year, a zero denominator or a denominator
 * whose sign makes the ratio meaningless (e.g. negative earnings for P/E) gives {@code null}, never an exception.
 * Valuation metrics are also {@code null} when the share price and the statements use different currencies
 * (e.g. an ADR priced in USD reporting in EUR), because the ratio would mix units.
 *
 * @param ticker                 company the metrics describe
 * @param reportingCurrency      ISO 4217 code of the statements (money values below are in it)
 * @param latestPeriodEnd        period end of the newest income statement the metrics are based on
 * @param price                  latest share price, trading currency
 * @param marketCap              price × shares outstanding
 * @param revenueLatestQuarter   revenue of the newest quarter
 * @param revenueTtm             revenue, trailing twelve months
 * @param netIncomeLatestQuarter GAAP net income of the newest quarter
 * @param netIncomeTtm           GAAP net income, trailing twelve months
 * @param epsTtm                 GAAP EPS = TTM net income ÷ shares outstanding
 * @param netMargin              TTM net income ÷ TTM revenue (fraction); {@code null} unless revenue is positive
 * @param ebitdaTtm              TTM operating income + TTM depreciation and amortisation (operating income as EBIT proxy)
 * @param priceToEarnings        market cap ÷ TTM net income (= price ÷ GAAP EPS); {@code null} unless earnings are positive
 * @param priceToSales           market cap ÷ TTM revenue; {@code null} unless revenue is positive
 * @param priceToBook            market cap ÷ total equity; {@code null} unless equity is positive
 * @param enterpriseValue        market cap + total debt − cash
 * @param evToEbitda             EV ÷ TTM EBITDA; {@code null} unless both are positive
 * @param totalDebt              short + long-term debt, latest balance sheet
 * @param netDebt                total debt − cash and equivalents (negative = net cash)
 * @param debtToEquity           total debt ÷ total equity; {@code null} unless equity is positive
 * @param freeCashFlowTtm        TTM operating cash flow − TTM capex
 * @param freeCashFlowMargin     TTM FCF ÷ TTM revenue (fraction); {@code null} unless revenue is positive and the newest
 *                               income and cash-flow statements end on the same date (both TTMs cover the same year)
 * @param freeCashFlowYield      TTM FCF ÷ market cap (fraction); {@code null} unless market cap is positive
 */
public record ComputedMetrics(
        String ticker,
        @Nullable String reportingCurrency,
        @Nullable LocalDate latestPeriodEnd,
        BigDecimal price,
        @Nullable BigDecimal marketCap,
        @Nullable BigDecimal revenueLatestQuarter,
        @Nullable BigDecimal revenueTtm,
        @Nullable BigDecimal netIncomeLatestQuarter,
        @Nullable BigDecimal netIncomeTtm,
        @Nullable BigDecimal epsTtm,
        @Nullable BigDecimal netMargin,
        @Nullable BigDecimal ebitdaTtm,
        @Nullable BigDecimal priceToEarnings,
        @Nullable BigDecimal priceToSales,
        @Nullable BigDecimal priceToBook,
        @Nullable BigDecimal enterpriseValue,
        @Nullable BigDecimal evToEbitda,
        @Nullable BigDecimal totalDebt,
        @Nullable BigDecimal netDebt,
        @Nullable BigDecimal debtToEquity,
        @Nullable BigDecimal freeCashFlowTtm,
        @Nullable BigDecimal freeCashFlowMargin,
        @Nullable BigDecimal freeCashFlowYield) {

    public ComputedMetrics {
        Objects.requireNonNull(ticker, "ticker");
        Objects.requireNonNull(price, "price");
    }
}
