package com.service.market_lens.marketdata.alphavantage;

import com.service.market_lens.config.MarketLensProperties;
import com.service.market_lens.marketdata.MarketDataProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AlphaVantageConfigurationTest {

    @Configuration
    @EnableConfigurationProperties({MarketLensProperties.class, AlphaVantageProperties.class})
    static class Props {
    }

    // Small context: our properties + the adapter config + a plain RestClient.Builder (stands in for Boot's).
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Props.class, AlphaVantageConfiguration.class)
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(JsonMapper.class, () -> JsonMapper.builder().build())
            .withPropertyValues(
                    "marketlens.llm.model=llama3.2",
                    "marketlens.cache.analysis-ttl=60m",
                    "marketlens.market-data.alphavantage.base-url=https://www.alphavantage.co",
                    "marketlens.market-data.alphavantage.connect-timeout=2s",
                    "marketlens.market-data.alphavantage.read-timeout=7s",
                    "marketlens.market-data.alphavantage.min-request-interval=1s");

    @Test
    void createsRestClientAndProviderWhenProviderIsAlphaVantage() {
        runner.withPropertyValues("marketlens.market-data.provider=alphavantage")
                .run(context -> {
                    assertThat(context).hasSingleBean(RestClient.class);
                    assertThat(context).getBean(MarketDataProvider.class)
                            .isInstanceOf(AlphaVantageMarketDataProvider.class);
                });
    }

    @Test
    void staysOffForAnotherProvider() {
        runner.withPropertyValues("marketlens.market-data.provider=someothervendor")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RestClient.class);
                    assertThat(context).doesNotHaveBean(MarketDataProvider.class);
                });
    }

    @Test
    void startsWithoutApiKey() {
        runner.withPropertyValues("marketlens.market-data.provider=alphavantage")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void bindsConnectionSettings() {
        runner.withPropertyValues("marketlens.market-data.provider=alphavantage")
                .run(context -> {
                    var props = context.getBean(AlphaVantageProperties.class);
                    assertThat(props.baseUrl()).isEqualTo(URI.create("https://www.alphavantage.co"));
                    assertThat(props.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(props.readTimeout()).isEqualTo(Duration.ofSeconds(7));
                    assertThat(props.minRequestInterval()).isEqualTo(Duration.ofSeconds(1));
                });
    }

    @Test
    void missingBaseUrlFailsStartup() {
        runner.withPropertyValues(
                        "marketlens.market-data.provider=alphavantage",
                        "marketlens.market-data.alphavantage.base-url=")
                .run(context -> assertThat(context).hasFailed());
    }
}
