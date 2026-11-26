package com.service.market_lens.api;

import com.service.market_lens.marketdata.MarketDataException;
import com.service.market_lens.marketdata.ProviderRateLimitedException;
import com.service.market_lens.marketdata.ProviderUnavailableException;
import com.service.market_lens.marketdata.TickerNotFoundException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Duration;

/**
 * The single place where exceptions become HTTP responses, as RFC 9457 {@link ProblemDetail}
 * ({@code application/problem+json}).
 * <p>
 * Spring MVC's own exceptions (validation → 400, unknown path → 404, wrong method → 405, ...) are handled by
 * {@link ResponseEntityExceptionHandler}. Domain failures are mapped here. Details never contain vendor URLs or keys,
 * and provider outages are described generically: the client cannot act on vendor internals.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MarketDataException.class)
    ResponseEntity<ProblemDetail> marketData(MarketDataException exception) {
        return switch (exception) {
            case TickerNotFoundException e -> problem(HttpStatus.NOT_FOUND, "Ticker not found",
                    "No market data found for ticker '" + e.ticker() + "'.", null);
            case ProviderRateLimitedException e -> {
                log.warn("{}", e.getMessage());
                yield problem(HttpStatus.SERVICE_UNAVAILABLE, "Market data rate limit reached",
                        "The market data provider's request limit has been reached. Try again later.",
                        e.retryAfter().map(ApiExceptionHandler::wholeSecondsRoundedUp).orElse(null));
            }
            case ProviderUnavailableException e -> {
                log.warn("{}", e.getMessage(), e.getCause());
                yield problem(HttpStatus.SERVICE_UNAVAILABLE, "Market data unavailable",
                        "The market data provider is not responding. Try again later.", null);
            }
        };
    }

    /** {@code Retry-After} is in whole seconds; round up so obeying clients never retry too early. At least 1. */
    private static String wholeSecondsRoundedUp(Duration delay) {
        long millis = Math.max(0, delay.toMillis());
        return String.valueOf(Math.max(1, (millis + 999) / 1000));
    }

    /** Last resort: log the cause, but never send stack traces or internal messages to the client. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error", "An unexpected error occurred.", null);
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String title, String detail,
                                                         @Nullable String retryAfterSeconds) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        var response = ResponseEntity.status(status);
        if (retryAfterSeconds != null) {
            response.header(HttpHeaders.RETRY_AFTER, retryAfterSeconds);
        }
        return response.body(body);
    }
}
