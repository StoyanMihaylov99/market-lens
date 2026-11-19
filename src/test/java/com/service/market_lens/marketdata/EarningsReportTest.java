package com.service.market_lens.marketdata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class EarningsReportTest {

    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 7, 31);

    @Test
    void allowsMissingEstimatesAndPeriodDetails() {
        // Small caps and ADRs often have no analyst coverage; that is valid data, not an error.
        var report = new EarningsReport("TINY", REPORT_DATE, null, null, null,
                new BigDecimal("-0.12"), null, new BigDecimal("1500000"), null, null);

        assertThat(report.epsEstimate()).isNull();
        assertThat(report.epsActual()).isEqualByComparingTo("-0.12");
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThatNullPointerException()
                .isThrownBy(() -> new EarningsReport(null, REPORT_DATE, null, null, null, null, null, null, null, null))
                .withMessageContaining("ticker");
        assertThatNullPointerException()
                .isThrownBy(() -> new EarningsReport("AAPL", null, null, null, null, null, null, null, null, null))
                .withMessageContaining("reportDate");
    }
}
