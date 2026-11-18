package com.service.market_lens.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MarketLensPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(MarketLensProperties.class)
    static class Config {
    }

    // A tiny Spring context with only our properties: fast, no web server.
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "marketlens.market-data.provider=fmp",
                    "marketlens.llm.model=llama3.2",
                    "marketlens.cache.analysis-ttl=60m");

    @Test
    void bindsValidConfiguration() {
        runner.run(context -> {
            var props = context.getBean(MarketLensProperties.class);
            assertThat(props.marketData().provider()).isEqualTo("fmp");
            assertThat(props.cache().analysisTtl()).isEqualTo(Duration.ofMinutes(60));
        });
    }

    @Test
    void startsWithoutApiKeys() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void blankProviderFailsStartup() {
        runner.withPropertyValues("marketlens.market-data.provider=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void nonPositiveTtlFailsStartup() {
        runner.withPropertyValues("marketlens.cache.analysis-ttl=0m")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void toStringNeverPrintsApiKeys() {
        runner.withPropertyValues(
                        "marketlens.market-data.api-key=secret-market-key",
                        "marketlens.llm.api-key=secret-llm-key")
                .run(context -> {
                    var text = context.getBean(MarketLensProperties.class).toString();
                    assertThat(text).doesNotContain("secret-market-key", "secret-llm-key").contains("****");
                });
    }
}
