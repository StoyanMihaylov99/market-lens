package com.service.market_lens.marketdata.alphavantage;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.service.market_lens.config.MarketLensProperties;
import com.service.market_lens.marketdata.MarketDataProvider;
import com.service.market_lens.marketdata.TickerNotFoundException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the <b>real mock-mode files</b> ({@code wiremock/mappings} + the fixtures, laid out exactly as
 * {@code compose.mock.yaml} mounts them) through the full adapter. A broken stub mapping fails {@code ./mvnw test}
 * instead of a demo. No Docker needed.
 */
class MockModeMappingsTest {

    @TempDir
    static Path wiremockRoot;

    private static WireMockServer server;
    private static MarketDataProvider provider;

    @BeforeAll
    static void start() throws IOException {
        copyDir(Path.of("wiremock/mappings"), wiremockRoot.resolve("mappings"));
        copyDir(Path.of("src/test/resources/fixtures/alphavantage"), wiremockRoot.resolve("__files"));
        server = new WireMockServer(options().dynamicPort().usingFilesUnderDirectory(wiremockRoot.toString()));
        server.start();

        var props = new AlphaVantageProperties(URI.create(server.baseUrl()), Duration.ofSeconds(1),
                Duration.ofSeconds(5), Duration.ZERO);
        var app = new MarketLensProperties(
                new MarketLensProperties.MarketData(AlphaVantageConfiguration.PROVIDER, "mock-key"),
                new MarketLensProperties.Llm("llama3.2", null),
                new MarketLensProperties.Cache(Duration.ofMinutes(60)));
        RestClient client = new AlphaVantageConfiguration().alphaVantageRestClient(RestClient.builder(), props, app);
        provider = new AlphaVantageMarketDataProvider(client, JsonMapper.builder().build(),
                new RequestThrottle(Duration.ZERO), Clock.systemUTC());
    }

    @AfterAll
    static void stop() {
        if (server != null) {
            server.stop();
        }
    }

    private static void copyDir(Path from, Path to) throws IOException {
        Files.createDirectories(to);
        try (Stream<Path> files = Files.list(from)) {
            files.forEach(f -> {
                try {
                    Files.copy(f, to.resolve(f.getFileName()));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    @Test
    void appleFundamentalsAndEarnings() {
        assertThat(provider.getFundamentals("AAPL").profile().name()).isEqualTo("Apple Inc");
        assertThat(provider.getRecentEarnings("AAPL", 4)).hasSize(4);
    }

    @Test
    void plugFundamentalsWithHandWrittenSearchStub() {
        var plug = provider.getFundamentals("PLUG");

        assertThat(plug.profile().name()).isEqualTo("Plug Power Inc");
        assertThat(plug.quote().price()).isEqualByComparingTo("1.03");
    }

    @Test
    void spyIndex() {
        assertThat(provider.getIndices(List.of("SPY")).getFirst().twoHundredDayAverage()).isNotNull();
    }

    @Test
    void anyOtherTickerBehavesLikeUnknown() {
        assertThatThrownBy(() -> provider.getFundamentals("MSFT")).isInstanceOf(TickerNotFoundException.class);
        assertThatThrownBy(() -> provider.getIndices(List.of("UNKNOWNXYZ")))
                .isInstanceOf(TickerNotFoundException.class);
    }
}
