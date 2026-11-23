package com.service.market_lens.marketdata.alphavantage;

import com.service.market_lens.config.MarketLensProperties;
import com.service.market_lens.marketdata.MarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Wires the Alpha Vantage adapter. Active only when {@code marketlens.market-data.provider=alphavantage}
 * ({@code MARKET_DATA_PROVIDER}), so another vendor's adapter can replace it with a config change.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "marketlens.market-data.provider", havingValue = AlphaVantageConfiguration.PROVIDER)
class AlphaVantageConfiguration {

    /** Value of {@code MARKET_DATA_PROVIDER} that selects this adapter; also the name used in exceptions. */
    static final String PROVIDER = "alphavantage";

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageConfiguration.class);

    /**
     * {@code RestClient} dedicated to Alpha Vantage: base URL, timeouts and API key are fixed here, so the
     * adapter only says which {@code function} and {@code symbol} it wants.
     *
     * @param builder Boot's auto-configured builder (a fresh instance per injection point), already carrying
     *                Jackson converters and observation/metrics support
     */
    @Bean
    RestClient alphaVantageRestClient(RestClient.Builder builder,
                                      AlphaVantageProperties properties,
                                      MarketLensProperties appProperties) {
        String apiKey = appProperties.marketData().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("MARKET_DATA_API_KEY is not set: Alpha Vantage requests will fail until it is configured");
        }
        var settings = HttpClientSettings.defaults()
                .withTimeouts(properties.connectTimeout(), properties.readTimeout());
        return builder
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .requestInterceptor(new ApiKeyInterceptor(apiKey))
                .build();
    }

    /**
     * The port implementation the rest of the app injects. {@code JsonMapper} is Boot's auto-configured Jackson 3
     * mapper; the clock stamps {@code CompanyFundamentals.fetchedAt}.
     */
    @Bean
    MarketDataProvider alphaVantageMarketDataProvider(RestClient alphaVantageRestClient,
                                                      JsonMapper jsonMapper,
                                                      AlphaVantageProperties properties) {
        return new AlphaVantageMarketDataProvider(alphaVantageRestClient, jsonMapper,
                new RequestThrottle(properties.minRequestInterval()), Clock.systemUTC());
    }
}
