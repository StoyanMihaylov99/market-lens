package com.service.market_lens.api;

import com.service.market_lens.marketdata.CompanyFundamentals;
import com.service.market_lens.marketdata.CompanyProfile;
import com.service.market_lens.marketdata.MarketDataProvider;
import com.service.market_lens.marketdata.Quote;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Web layer only (@WebMvcTest): the provider is a Mockito mock, no HTTP to any vendor. */
class RawFundamentalsControllerTest {

    private static final Instant AS_OF = Instant.parse("2026-11-19T00:00:00Z");

    private static CompanyFundamentals apple() {
        return new CompanyFundamentals(
                new CompanyProfile("AAPL", "Apple Inc", null, "USD", null, null, "US"),
                new Quote("AAPL", new BigDecimal("349.21"), null, null, AS_OF),
                List.of(), List.of(), List.of(), AS_OF);
    }

    @Nested
    @WebMvcTest(RawFundamentalsController.class)
    @ActiveProfiles("local")
    class InLocalProfile {

        @Autowired
        MockMvcTester mvc;

        @MockitoBean
        MarketDataProvider provider;

        @Test
        void returnsFundamentalsAndUpperCasesTicker() {
            when(provider.getFundamentals("AAPL")).thenReturn(apple());

            assertThat(mvc.get().uri("/api/v1/companies/aapl/raw"))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.profile.name", v -> v.assertThat().isEqualTo("Apple Inc"))
                    .hasPathSatisfying("$.quote.price", v -> v.assertThat().asNumber().isEqualTo(349.21))
                    .hasPathSatisfying("$.fetchedAt", v -> v.assertThat().isEqualTo("2026-11-19T00:00:00Z"));
        }

        @Test
        void invalidTickerIsRejectedBeforeCallingProvider() {
            // note: "AAPL;x" would NOT test this: Spring strips ";..." from paths (matrix variables) → "AAPL"
            assertThat(mvc.get().uri("/api/v1/companies/{t}/raw", "AAPL!")).hasStatus(400);
            assertThat(mvc.get().uri("/api/v1/companies/{t}/raw", "<script>")).hasStatus(400);
            assertThat(mvc.get().uri("/api/v1/companies/{t}/raw", "WAYTOOLONGTICKER")).hasStatus(400);

            verify(provider, never()).getFundamentals(anyString());
        }
    }

    @Nested
    @WebMvcTest(RawFundamentalsController.class)
    class WithoutDevProfile {

        @Autowired
        MockMvcTester mvc;

        @MockitoBean
        MarketDataProvider provider;

        @Test
        void endpointDoesNotExist() {
            assertThat(mvc.get().uri("/api/v1/companies/AAPL/raw")).hasStatus(404);
        }
    }
}
