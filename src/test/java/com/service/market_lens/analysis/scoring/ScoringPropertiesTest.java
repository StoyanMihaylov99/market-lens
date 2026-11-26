package com.service.market_lens.analysis.scoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(ScoringProperties.class)
    static class Config {
    }

    /** Loads the real application.yaml and the scoring.yaml it imports, so these tests also guard the shipped defaults. */
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(Config.class, ScoringConfiguration.class);

    @Test
    void scoringYamlDefaultsMatchTestData() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            // by value: YAML turns 0.10 into 0.1, and BigDecimal.equals compares scale too
            assertThat(context.getBean(ScoringProperties.class))
                    .usingRecursiveComparison()
                    .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                    .isEqualTo(ScoringTestData.properties());
            assertThat(context).hasSingleBean(ScoringEngine.class);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "marketlens.scoring.recommendation.sell=65",               // sell must be below buy
            "marketlens.scoring.recommendation.buy=101",
            "marketlens.scoring.confidence.medium=0.9",                // medium must be below high
            "marketlens.scoring.confidence.high=1.5",
            "marketlens.scoring.valuation.fair=40",                    // cheap < fair < expensive
            "marketlens.scoring.valuation.cheap=0",
            "marketlens.scoring.leverage.low=-1",
            "marketlens.scoring.leverage.high=1.0",                    // moderate < high
            "marketlens.scoring.cash-flow.moderate-yield=0.05",        // moderate < high
            "marketlens.scoring.profitability.high-margin=0.05",
            "marketlens.scoring.profitability.max-points=0",
            "marketlens.scoring.valuation.expensive="})
    void invalidConfigurationFailsStartup(String property) {
        runner.withPropertyValues(property).run(context -> assertThat(context).hasFailed());
    }
}
