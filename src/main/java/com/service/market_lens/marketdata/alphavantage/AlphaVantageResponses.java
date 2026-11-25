package com.service.market_lens.marketdata.alphavantage;

import com.service.market_lens.marketdata.ProviderRateLimitedException;
import com.service.market_lens.marketdata.ProviderUnavailableException;
import com.service.market_lens.marketdata.TickerNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Classifies an Alpha Vantage response body <b>before</b> it is mapped. Needed because Alpha Vantage reports most
 * errors with HTTP 200 and a special body; the HTTP status alone says almost nothing.
 *
 * <table>
 *   <caption>Body → outcome</caption>
 *   <tr><td>{@code {"Error Message": ...}}</td><td>{@link TickerNotFoundException} (e.g. unknown symbol for a time series)</td></tr>
 *   <tr><td>any {@code Note}/{@code Information}</td><td>{@link ProviderRateLimitedException} (daily cap or burst limit)</td></tr>
 *   <tr><td>{@code {}}, {@code {"Global Quote": {}}}, {@code {"bestMatches": []}}</td><td>{@link TickerNotFoundException}</td></tr>
 *   <tr><td>not a JSON object</td><td>{@link ProviderUnavailableException}</td></tr>
 * </table>
 * Messages never contain the request URL or key; vendor message text is not copied either (it can be long and change).
 * <p>
 * Why {@code Information} saying "premium endpoint" is <em>not</em> treated as a paywall: on 2026-11-23 a free
 * function ({@code SYMBOL_SEARCH}) answered that way when called 1 s after another request, and returned data when
 * called alone. The text is unreliable, so it is logged (WARN) for diagnosis and the outcome is always "rate limited".
 */
final class AlphaVantageResponses {

    private static final String PROVIDER = AlphaVantageConfiguration.PROVIDER;
    private static final Logger log = LoggerFactory.getLogger(AlphaVantageResponses.class);
    private static final int MAX_LOGGED_NOTICE = 300;

    private AlphaVantageResponses() {
    }

    /**
     * @param function the Alpha Vantage function called, used only in error messages
     * @param symbol   the ticker or symbol requested, reported in {@link TickerNotFoundException}
     */
    static void check(String function, String symbol, JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new ProviderUnavailableException(PROVIDER, "unexpected response for " + function, null);
        }
        if (body.has("Error Message")) {
            throw new TickerNotFoundException(symbol);
        }
        String notice = body.has("Note") ? body.get("Note").asString("")
                : body.has("Information") ? body.get("Information").asString("") : null;
        if (notice != null) {
            // vendor text contains no key; truncated so a long notice can't flood the log
            log.warn("Alpha Vantage refused {}: {}", function,
                    notice.length() > MAX_LOGGED_NOTICE ? notice.substring(0, MAX_LOGGED_NOTICE) + "..." : notice);
            throw new ProviderRateLimitedException(PROVIDER, null);
        }
        if (hasNoData(body)) {
            throw new TickerNotFoundException(symbol);
        }
    }

    /**
     * True for {@code {}} and for bodies whose every field is an empty object or array. A known company without data
     * still has e.g. {@code "symbol": "X"}, so it is not treated as unknown.
     */
    private static boolean hasNoData(JsonNode body) {
        for (JsonNode value : body.values()) {
            if (!(value.isContainer() && value.isEmpty())) {
                return false;
            }
        }
        return true;
    }
}
