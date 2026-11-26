package com.service.market_lens.metrics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MetricsMathTest {

    private static BigDecimal d(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    @ParameterizedTest(name = "{0} / {1} = {2}")
    @CsvSource(nullValues = "null", value = {
            "10, 4, 2.5000",
            "1, 3, 0.3333",
            "2, 3, 0.6667",
            "-1, 4, -0.2500",
            "1, -4, -0.2500",
            "0.00005, 1, 0.0000",   // half-even: ...5 rounds to the even neighbour 0
            "0.00015, 1, 0.0002",   // ...and 0.00015 to 0.0002
            "1, 0, null",
            "null, 4, null",
            "4, null, null"})
    void divideRoundsToFourPlacesAndToleratesZeroAndMissing(String numerator, String denominator, String expected) {
        assertThat(MetricsMath.divide(d(numerator), d(denominator))).isEqualTo(d(expected));
    }

    @ParameterizedTest(name = "{0} / {1} = {2}")
    @CsvSource(nullValues = "null", value = {
            "10, 4, 2.5000",
            "-10, 4, -2.5000",
            "10, 0, null",
            "10, -4, null",
            "10, null, null"})
    void divideByPositiveRejectsNonPositiveDenominator(String numerator, String denominator, String expected) {
        assertThat(MetricsMath.divideByPositive(d(numerator), d(denominator))).isEqualTo(d(expected));
    }

    @Test
    void addAndSubtractPropagateMissingValues() {
        assertThat(MetricsMath.add(d("1.5"), d("2"))).isEqualByComparingTo("3.5");
        assertThat(MetricsMath.add(null, d("2"))).isNull();
        assertThat(MetricsMath.add(d("2"), null)).isNull();
        assertThat(MetricsMath.subtract(d("5"), d("7"))).isEqualByComparingTo("-2");
        assertThat(MetricsMath.subtract(null, d("2"))).isNull();
        assertThat(MetricsMath.subtract(d("2"), null)).isNull();
    }

    @Test
    void moneyIsNotRounded() {
        assertThat(MetricsMath.add(d("123456789012345.678"), d("0.001"))).isEqualTo(d("123456789012345.679"));
    }

    @Test
    void isPositive() {
        assertThat(MetricsMath.isPositive(d("0.0001"))).isTrue();
        assertThat(MetricsMath.isPositive(BigDecimal.ZERO)).isFalse();
        assertThat(MetricsMath.isPositive(d("-1"))).isFalse();
        assertThat(MetricsMath.isPositive(null)).isFalse();
    }
}
