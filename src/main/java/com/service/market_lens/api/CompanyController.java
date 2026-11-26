package com.service.market_lens.api;

import com.service.market_lens.analysis.FundamentalsService;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

/**
 * Company endpoints. Lives in {@code api/} while the API is small (CONTEXT.md, package rule 1).
 * Ticker validation and upper-casing happen here, at the edge; errors are mapped by {@link ApiExceptionHandler}.
 */
@RestController
@RequestMapping("/api/v1/companies")
class CompanyController {

    private final FundamentalsService fundamentalsService;

    CompanyController(FundamentalsService fundamentalsService) {
        this.fundamentalsService = fundamentalsService;
    }

    /** Computed metrics + rule-based score. An invalid ticker is rejected with 400 before any provider call. */
    @GetMapping("/{ticker}/fundamentals")
    FundamentalsResponse fundamentals(@PathVariable @Pattern(regexp = Tickers.PATTERN) String ticker) {
        return FundamentalsResponse.from(fundamentalsService.analyze(ticker.toUpperCase(Locale.ROOT)));
    }
}
