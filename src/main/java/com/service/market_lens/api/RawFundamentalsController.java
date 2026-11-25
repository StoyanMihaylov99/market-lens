package com.service.market_lens.api;

import com.service.market_lens.marketdata.CompanyFundamentals;
import com.service.market_lens.marketdata.MarketDataProvider;
import jakarta.validation.constraints.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * <b>Temporary</b> debug endpoint (Phase 1): shows what the market data provider returns, before metrics exist.
 * Registered only in the {@code local} and {@code mock} profiles, so it never ships in a default deployment.
 * Remove once {@code GET /companies/{ticker}/fundamentals} (Phase 2) exists.
 * <p>
 * Ticker validation and upper-casing happen here, at the API edge, as the {@link MarketDataProvider} contract expects.
 * Domain exceptions are not mapped to HTTP statuses yet (the {@code @RestControllerAdvice} is a Phase 2 task).
 */
@RestController
@Profile({"local", "mock"})
@RequestMapping("/api/v1/companies")
class RawFundamentalsController {

    /** Letters, digits, dot and dash (e.g. {@code BRK.B}), 1–10 characters. */
    static final String TICKER = "[A-Za-z0-9.\\-]{1,10}";

    private final MarketDataProvider provider;

    RawFundamentalsController(MarketDataProvider provider) {
        this.provider = provider;
    }

    /** Invalid tickers are rejected with 400 by Spring's built-in method validation before the provider is called. */
    @GetMapping("/{ticker}/raw")
    CompanyFundamentals raw(@PathVariable @Pattern(regexp = TICKER) String ticker) {
        return provider.getFundamentals(ticker.toUpperCase(Locale.ROOT));
    }
}
