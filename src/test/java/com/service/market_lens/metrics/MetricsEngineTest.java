package com.service.market_lens.metrics;

import com.service.market_lens.marketdata.CompanyFundamentals;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static com.service.market_lens.metrics.FundamentalsBuilder.LATEST;
import static com.service.market_lens.metrics.FundamentalsBuilder.bn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Expected values are calculated by hand from {@link FundamentalsBuilder}'s round defaults (see its Javadoc).
 * Each metric is checked with a normal, zero, negative and missing input.
 */
class MetricsEngineTest {

    private static ComputedMetrics compute(FundamentalsBuilder builder) {
        return MetricsEngine.compute(builder.build());
    }

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    @Test
    void computesEveryMetricFromCompleteData() {
        ComputedMetrics m = compute(new FundamentalsBuilder());

        assertThat(m.ticker()).isEqualTo("TEST");
        assertThat(m.reportingCurrency()).isEqualTo("USD");
        assertThat(m.latestPeriodEnd()).isEqualTo(LATEST);
        assertThat(m.price()).isEqualByComparingTo("100");
        assertThat(m.marketCap()).isEqualByComparingTo(bn("100"));            // 100 × 1bn shares
        assertThat(m.revenueLatestQuarter()).isEqualByComparingTo(bn("25"));
        assertThat(m.revenueTtm()).isEqualByComparingTo(bn("100"));
        assertThat(m.netIncomeLatestQuarter()).isEqualByComparingTo(bn("2.5"));
        assertThat(m.netIncomeTtm()).isEqualByComparingTo(bn("10"));
        assertThat(m.epsTtm()).isEqualTo(d("10.0000"));                       // 10bn / 1bn
        assertThat(m.netMargin()).isEqualTo(d("0.1000"));                     // 10 / 100
        assertThat(m.ebitdaTtm()).isEqualByComparingTo(bn("14"));             // 12 + 2
        assertThat(m.priceToEarnings()).isEqualTo(d("10.0000"));              // 100 / 10
        assertThat(m.priceToSales()).isEqualTo(d("1.0000"));                  // 100 / 100
        assertThat(m.priceToBook()).isEqualTo(d("2.5000"));                   // 100 / 40
        assertThat(m.enterpriseValue()).isEqualByComparingTo(bn("130"));      // 100 + 50 − 20
        assertThat(m.evToEbitda()).isEqualTo(d("9.2857"));                    // 130 / 14 = 9.285714…
        assertThat(m.totalDebt()).isEqualByComparingTo(bn("50"));
        assertThat(m.netDebt()).isEqualByComparingTo(bn("30"));               // 50 − 20
        assertThat(m.debtToEquity()).isEqualTo(d("1.2500"));                  // 50 / 40
        assertThat(m.freeCashFlowTtm()).isEqualByComparingTo(bn("8"));        // 12 − 4
        assertThat(m.freeCashFlowMargin()).isEqualTo(d("0.0800"));            // 8 / 100
        assertThat(m.freeCashFlowYield()).isEqualTo(d("0.0800"));             // 8 / 100
    }

    @Test
    void ratiosHaveFourDecimalPlacesRoundedHalfEven() {
        // 1 / 3 → 0.3333; 2 / 3 → 0.6667
        ComputedMetrics m = compute(new FundamentalsBuilder().debt(bn("10")).equity(bn("30")));
        assertThat(m.debtToEquity()).isEqualTo(d("0.3333"));
        assertThat(compute(new FundamentalsBuilder().debt(bn("20")).equity(bn("30"))).debtToEquity())
                .isEqualTo(d("0.6667"));
    }

    @Nested
    class Earnings {

        static Stream<Arguments> netIncomePerQuarter() {
            return Stream.of(
                    arguments("positive", bn("2.5"), d("10.0000"), d("0.1000"), d("10.0000")),
                    arguments("zero", BigDecimal.ZERO, null, d("0.0000"), d("0.0000")),
                    arguments("negative", bn("-1"), null, d("-0.0400"), d("-4.0000")),
                    arguments("missing", null, null, null, null));
        }

        @ParameterizedTest(name = "{0} net income")
        @MethodSource("netIncomePerQuarter")
        void peNeedsPositiveEarnings(String label, BigDecimal netIncome,
                                     BigDecimal pe, BigDecimal netMargin, BigDecimal eps) {
            ComputedMetrics m = compute(new FundamentalsBuilder().netIncome(netIncome));
            assertThat(m.priceToEarnings()).isEqualTo(pe);
            assertThat(m.netMargin()).isEqualTo(netMargin);
            assertThat(m.epsTtm()).isEqualTo(eps);
        }

        @Test
        void epsNeedsShareCount() {
            ComputedMetrics m = compute(new FundamentalsBuilder().shares(null).marketCap(bn("100")));
            assertThat(m.epsTtm()).isNull();
            assertThat(m.priceToEarnings()).isEqualTo(d("10.0000")); // valuation still works from the vendor market cap
        }

        @Test
        void zeroSharesGiveNoEps() {
            assertThat(compute(new FundamentalsBuilder().shares(0L)).epsTtm()).isNull();
        }
    }

    @Nested
    class Revenue {

        static Stream<Arguments> revenuePerQuarter() {
            return Stream.of(
                    arguments("positive", bn("25"), d("1.0000")),
                    arguments("zero", BigDecimal.ZERO, null),
                    arguments("negative", bn("-1"), null),
                    arguments("missing", null, null));
        }

        @ParameterizedTest(name = "{0} revenue")
        @MethodSource("revenuePerQuarter")
        void revenueBasedRatiosNeedPositiveRevenue(String label, BigDecimal revenue, BigDecimal priceToSales) {
            ComputedMetrics m = compute(new FundamentalsBuilder().revenue(revenue));
            assertThat(m.priceToSales()).isEqualTo(priceToSales);
            if (priceToSales == null) {
                assertThat(m.netMargin()).isNull();
                assertThat(m.freeCashFlowMargin()).isNull();
            }
        }
    }

    @Nested
    class BalanceSheet {

        static Stream<Arguments> equity() {
            return Stream.of(
                    arguments("positive", bn("40"), d("1.2500"), d("2.5000")),
                    arguments("zero", BigDecimal.ZERO, null, null),
                    arguments("negative", bn("-5"), null, null),
                    arguments("missing", null, null, null));
        }

        @ParameterizedTest(name = "{0} equity")
        @MethodSource("equity")
        void equityBasedRatiosNeedPositiveEquity(String label, BigDecimal equity,
                                                 BigDecimal debtToEquity, BigDecimal priceToBook) {
            ComputedMetrics m = compute(new FundamentalsBuilder().equity(equity));
            assertThat(m.debtToEquity()).isEqualTo(debtToEquity);
            assertThat(m.priceToBook()).isEqualTo(priceToBook);
        }

        @Test
        void debtFreeCompanyHasZeroLeverageAndNetCash() {
            ComputedMetrics m = compute(new FundamentalsBuilder().debt(BigDecimal.ZERO));
            assertThat(m.debtToEquity()).isEqualTo(d("0.0000"));
            assertThat(m.netDebt()).isEqualByComparingTo(bn("-20"));
            assertThat(m.enterpriseValue()).isEqualByComparingTo(bn("80"));
        }

        @Test
        void missingDebtLeavesDebtMetricsAndEnterpriseValueEmpty() {
            ComputedMetrics m = compute(new FundamentalsBuilder().debt(null));
            assertThat(m.totalDebt()).isNull();
            assertThat(m.netDebt()).isNull();
            assertThat(m.debtToEquity()).isNull();
            assertThat(m.enterpriseValue()).isNull();
            assertThat(m.evToEbitda()).isNull();
        }

        @Test
        void missingCashLeavesNetDebtAndEnterpriseValueEmpty() {
            ComputedMetrics m = compute(new FundamentalsBuilder().cash(null));
            assertThat(m.netDebt()).isNull();
            assertThat(m.enterpriseValue()).isNull();
            assertThat(m.debtToEquity()).isEqualTo(d("1.2500"));
        }
    }

    @Nested
    class EnterpriseValue {

        static Stream<Arguments> ebitdaPerQuarter() {
            return Stream.of(
                    arguments("positive", bn("3"), d("9.2857")),
                    arguments("zero", bn("-0.5"), null),       // operating income −0.5 + D&A 0.5 = 0
                    arguments("negative", bn("-2"), null),
                    arguments("missing", null, null));
        }

        @ParameterizedTest(name = "{0} EBITDA")
        @MethodSource("ebitdaPerQuarter")
        void evToEbitdaNeedsPositiveEbitda(String label, BigDecimal operatingIncome, BigDecimal evToEbitda) {
            assertThat(compute(new FundamentalsBuilder().operatingIncome(operatingIncome)).evToEbitda())
                    .isEqualTo(evToEbitda);
        }

        @Test
        void missingDepreciationLeavesEbitdaEmpty() {
            ComputedMetrics m = compute(new FundamentalsBuilder().depreciation(null));
            assertThat(m.ebitdaTtm()).isNull();
            assertThat(m.evToEbitda()).isNull();
        }

        @Test
        void cashRichCompanyWithNegativeEnterpriseValueHasNoEvToEbitda() {
            ComputedMetrics m = compute(new FundamentalsBuilder().cash(bn("200")));
            assertThat(m.enterpriseValue()).isEqualByComparingTo(bn("-50")); // 100 + 50 − 200
            assertThat(m.evToEbitda()).isNull();
        }
    }

    @Nested
    class CashFlow {

        static Stream<Arguments> operatingCashFlowPerQuarter() {
            return Stream.of(
                    arguments("positive", bn("3"), bn("8"), d("0.0800")),
                    arguments("zero", BigDecimal.ZERO, bn("-4"), d("-0.0400")),
                    arguments("negative", bn("-2"), bn("-12"), d("-0.1200")),
                    arguments("missing", null, null, null));
        }

        @ParameterizedTest(name = "{0} operating cash flow")
        @MethodSource("operatingCashFlowPerQuarter")
        void freeCashFlowIsOperatingCashFlowMinusCapex(String label, BigDecimal ocf,
                                                       BigDecimal fcf, BigDecimal fcfYield) {
            ComputedMetrics m = compute(new FundamentalsBuilder().operatingCashFlow(ocf));
            if (fcf == null) {
                assertThat(m.freeCashFlowTtm()).isNull();
            } else {
                assertThat(m.freeCashFlowTtm()).isEqualByComparingTo(fcf);
            }
            assertThat(m.freeCashFlowYield()).isEqualTo(fcfYield);
        }

        @Test
        void missingCapexLeavesFreeCashFlowEmpty() {
            ComputedMetrics m = compute(new FundamentalsBuilder().capex(null));
            assertThat(m.freeCashFlowTtm()).isNull();
            assertThat(m.freeCashFlowMargin()).isNull();
            assertThat(m.freeCashFlowYield()).isNull();
        }

        @Test
        void freeCashFlowMarginNeedsIncomeAndCashFlowToEndWithTheSameQuarter() {
            // income already has the newest quarter, cash flow not yet (vendor endpoints updated at different times)
            CompanyFundamentals full = new FundamentalsBuilder().quarters(5).build();
            CompanyFundamentals lagging = new CompanyFundamentals(full.profile(), full.quote(), full.incomeStatements(),
                    full.balanceSheets(), full.cashFlowStatements().subList(1, 5), full.fetchedAt());

            ComputedMetrics m = MetricsEngine.compute(lagging);

            assertThat(m.freeCashFlowTtm()).isEqualByComparingTo(bn("8"));   // still a valid trailing year...
            assertThat(m.freeCashFlowYield()).isEqualTo(d("0.0800"));          // ...so price-based yield is fine
            assertThat(m.freeCashFlowMargin()).isNull();                       // but not divided by another year's revenue
        }

        @Test
        void freeCashFlowMarginNeedsCashFlowStatements() {
            CompanyFundamentals full = new FundamentalsBuilder().build();
            CompanyFundamentals noCashFlow = new CompanyFundamentals(full.profile(), full.quote(),
                    full.incomeStatements(), full.balanceSheets(), List.of(), full.fetchedAt());
            assertThat(MetricsEngine.compute(noCashFlow).freeCashFlowMargin()).isNull();
        }

        @Test
        void zeroCapexMeansFreeCashFlowEqualsOperatingCashFlow() {
            assertThat(compute(new FundamentalsBuilder().capex(BigDecimal.ZERO)).freeCashFlowTtm())
                    .isEqualByComparingTo(bn("12"));
        }
    }

    @Nested
    class MarketCap {

        @Test
        void prefersVendorMarketCapOverPriceTimesShares() {
            ComputedMetrics m = compute(new FundamentalsBuilder().marketCap(bn("200")));
            assertThat(m.marketCap()).isEqualByComparingTo(bn("200"));
            assertThat(m.priceToEarnings()).isEqualTo(d("20.0000"));
        }

        @Test
        void withoutMarketCapOrSharesEveryValuationMetricIsEmpty() {
            ComputedMetrics m = compute(new FundamentalsBuilder().shares(null));
            assertThat(m.marketCap()).isNull();
            assertThat(m.priceToEarnings()).isNull();
            assertThat(m.priceToSales()).isNull();
            assertThat(m.priceToBook()).isNull();
            assertThat(m.enterpriseValue()).isNull();
            assertThat(m.evToEbitda()).isNull();
            assertThat(m.freeCashFlowYield()).isNull();
            // ...while statement-only metrics are unaffected
            assertThat(m.netMargin()).isEqualTo(d("0.1000"));
            assertThat(m.debtToEquity()).isEqualTo(d("1.2500"));
        }

        @Test
        void zeroPriceGivesNoValuation() {
            ComputedMetrics m = compute(new FundamentalsBuilder().price("0"));
            assertThat(m.priceToEarnings()).isNull();
            assertThat(m.priceToSales()).isNull();
            assertThat(m.priceToBook()).isNull();
            assertThat(m.freeCashFlowYield()).isNull();
        }
    }

    @Nested
    class Currency {

        @Test
        void differentTradingAndReportingCurrencyDisablesValuationOnly() {
            ComputedMetrics m = compute(new FundamentalsBuilder().tradingCurrency("USD").reportingCurrency("EUR"));
            assertThat(m.reportingCurrency()).isEqualTo("EUR");
            assertThat(m.marketCap()).isEqualByComparingTo(bn("100")); // still correct, in the trading currency
            assertThat(m.priceToEarnings()).isNull();
            assertThat(m.priceToSales()).isNull();
            assertThat(m.priceToBook()).isNull();
            assertThat(m.enterpriseValue()).isNull();
            assertThat(m.freeCashFlowYield()).isNull();
            assertThat(m.netMargin()).isEqualTo(d("0.1000"));
            assertThat(m.debtToEquity()).isEqualTo(d("1.2500"));
        }

        @Test
        void currencyComparisonIgnoresCase() {
            assertThat(compute(new FundamentalsBuilder().tradingCurrency("usd")).priceToEarnings()).isNotNull();
        }

        @Test
        void unknownCurrencyIsAssumedToMatch() {
            assertThat(compute(new FundamentalsBuilder().tradingCurrency(null)).priceToEarnings()).isNotNull();
            ComputedMetrics m = compute(new FundamentalsBuilder().reportingCurrency(null));
            assertThat(m.reportingCurrency()).isNull();
            assertThat(m.priceToEarnings()).isNotNull();
        }
    }

    @Nested
    class IncompleteHistory {

        @Test
        void fewerThanFourQuartersGiveLatestValuesButNoTtm() {
            ComputedMetrics m = compute(new FundamentalsBuilder().quarters(3));
            assertThat(m.revenueLatestQuarter()).isEqualByComparingTo(bn("25"));
            assertThat(m.netIncomeLatestQuarter()).isEqualByComparingTo(bn("2.5"));
            assertThat(m.revenueTtm()).isNull();
            assertThat(m.netIncomeTtm()).isNull();
            assertThat(m.ebitdaTtm()).isNull();
            assertThat(m.freeCashFlowTtm()).isNull();
            assertThat(m.priceToEarnings()).isNull();
            assertThat(m.debtToEquity()).isEqualTo(d("1.2500")); // balance sheet metrics need one quarter only
        }

        @Test
        void noStatementsAtAllStillGivePriceAndMarketCap() {
            ComputedMetrics m = compute(new FundamentalsBuilder().quarters(0));
            assertThat(m.price()).isEqualByComparingTo("100");
            assertThat(m.marketCap()).isEqualByComparingTo(bn("100"));
            assertThat(m.latestPeriodEnd()).isNull();
            assertThat(m.reportingCurrency()).isNull();
            assertThat(m).extracting(ComputedMetrics::revenueLatestQuarter, ComputedMetrics::netIncomeLatestQuarter,
                            ComputedMetrics::revenueTtm, ComputedMetrics::netIncomeTtm, ComputedMetrics::epsTtm,
                            ComputedMetrics::netMargin, ComputedMetrics::ebitdaTtm, ComputedMetrics::priceToEarnings,
                            ComputedMetrics::priceToSales, ComputedMetrics::priceToBook, ComputedMetrics::enterpriseValue,
                            ComputedMetrics::evToEbitda, ComputedMetrics::totalDebt, ComputedMetrics::netDebt,
                            ComputedMetrics::debtToEquity, ComputedMetrics::freeCashFlowTtm,
                            ComputedMetrics::freeCashFlowMargin, ComputedMetrics::freeCashFlowYield)
                    .containsOnlyNulls();
        }
    }
}
