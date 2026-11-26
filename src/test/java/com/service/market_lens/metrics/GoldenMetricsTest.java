package com.service.market_lens.metrics;

import com.service.market_lens.marketdata.alphavantage.AlphaVantageFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden tests: real recorded Alpha Vantage data (fixtures, mapped by the real adapter) → metrics.
 * Expected values were calculated by hand from the raw fixture figures listed in each comment, independently of
 * the code under test. If a fixture is re-recorded, recalculate them.
 */
class GoldenMetricsTest {

    private static BigDecimal d(String value) {
        return new BigDecimal(value);
    }

    /**
     * AAPL, quarters ending 2026-09-30, 06-30, 03-31, 2025-12-31 (USD millions):
     * revenue 114,477 + 109,417 + 111,184 + 143,756 = 478,834;
     * net income 30,711 + 29,789 + 29,578 + 42,097 = 132,175;
     * operating income 35,631 + 35,695 + 35,885 + 50,852 = 158,063; D&amp;A 3,450 + 3,320 + 3,439 + 3,214 = 13,423;
     * OCF 28,101 + 34,369 + 28,702 + 53,925 = 145,097; capex 2,242 + 2,455 + 1,971 + 2,373 = 9,041.
     * Balance sheet 2026-09-30: cash 36,305; debt 80,111; equity 112,093; shares 14,630,318,000. Price 349.21.
     */
    @Test
    void apple() {
        ComputedMetrics m = MetricsEngine.compute(AlphaVantageFixtures.fundamentals("AAPL"));

        assertThat(m.reportingCurrency()).isEqualTo("USD");
        assertThat(m.latestPeriodEnd()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(m.price()).isEqualByComparingTo("349.21");
        assertThat(m.marketCap()).isEqualByComparingTo("5109053348780");      // 349.21 × 14,630,318,000
        assertThat(m.revenueLatestQuarter()).isEqualByComparingTo("114477000000");
        assertThat(m.revenueTtm()).isEqualByComparingTo("478834000000");
        assertThat(m.netIncomeLatestQuarter()).isEqualByComparingTo("30711000000");
        assertThat(m.netIncomeTtm()).isEqualByComparingTo("132175000000");
        assertThat(m.epsTtm()).isEqualTo(d("9.0343"));                         // 132,175M / 14,630.318M shares
        assertThat(m.netMargin()).isEqualTo(d("0.2760"));                      // 132,175 / 478,834
        assertThat(m.ebitdaTtm()).isEqualByComparingTo("171486000000");        // 158,063 + 13,423
        assertThat(m.priceToEarnings()).isEqualTo(d("38.6537"));               // 5,109,053.35M / 132,175M
        assertThat(m.priceToSales()).isEqualTo(d("10.6698"));                  // / 478,834M
        assertThat(m.priceToBook()).isEqualTo(d("45.5787"));                   // / 112,093M
        assertThat(m.enterpriseValue()).isEqualByComparingTo("5152859348780"); // + 80,111M − 36,305M
        assertThat(m.evToEbitda()).isEqualTo(d("30.0483"));                    // / 171,486M
        assertThat(m.totalDebt()).isEqualByComparingTo("80111000000");
        assertThat(m.netDebt()).isEqualByComparingTo("43806000000");
        assertThat(m.debtToEquity()).isEqualTo(d("0.7147"));                   // 80,111 / 112,093
        assertThat(m.freeCashFlowTtm()).isEqualByComparingTo("136056000000");  // 145,097 − 9,041
        assertThat(m.freeCashFlowMargin()).isEqualTo(d("0.2841"));             // 136,056 / 478,834
        assertThat(m.freeCashFlowYield()).isEqualTo(d("0.0266"));              // 136,056M / 5,109,053.35M
    }

    /**
     * PLUG (loss-making, negative cash flow), USD thousands: net income 99,766 − 188,207 − 245,304 − 845,970
     * = −1,179,715; revenue 200,602 + 178,299 + 163,513 + 225,220 = 767,634;
     * OCF −76,420 − 94,064 − 150,041 − 148,643 = −469,168; capex 14,802 + 6,304 + 8,114 + 9,062 = 38,282;
     * debt 888,483; equity 705,896.
     */
    @Test
    void plugPower() {
        ComputedMetrics m = MetricsEngine.compute(AlphaVantageFixtures.fundamentals("PLUG"));

        assertThat(m.netIncomeTtm()).isEqualByComparingTo("-1179715000");
        assertThat(m.priceToEarnings()).isNull();                              // no P/E for losses
        assertThat(m.netMargin()).isEqualTo(d("-1.5368"));                     // −1,179,715 / 767,634
        assertThat(m.freeCashFlowTtm()).isEqualByComparingTo("-507450000");   // −469,168 − 38,282
        assertThat(m.debtToEquity()).isEqualTo(d("1.2587"));                   // 888,483 / 705,896
    }
}
