package com.service.market_lens.marketdata.alphavantage;

import com.service.market_lens.marketdata.ProviderRateLimitedException;
import com.service.market_lens.marketdata.ProviderUnavailableException;
import com.service.market_lens.marketdata.TickerNotFoundException;
import tools.jackson.databind.JsonNode;

import java.util.Locale;

/**
 * Classifies an Alpha Vantage response body <b>before</b> it is mapped. Needed because Alpha Vantage reports most
 * errors with HTTP 200 and a special body; the HTTP status alone says almost nothing.
 *
 * <table>
 *   <caption>Body → outcome</caption>
 *   <tr><td>{@code {"Error Message": ...}}</td><td>{@link TickerNotFoundException} (e.g. unknown symbol for a time series)</td></tr>
 *   <tr><td>{@code Note}/{@code Information} mentioning "premium"</td><td>{@link ProviderUnavailableException}: the function moved behind the paywall; retrying won't help</td></tr>
 *   <tr><td>any other {@code Note}/{@code Information}</td><td>{@link ProviderRateLimitedException} (daily cap or burst limit)</td></tr>
 *   <tr><td>{@code {}}, {@code {"Global Quote": {}}}, {@code {"bestMatches": []}}</td><td>{@link TickerNotFoundException}</td></tr>
 *   <tr><td>not a JSON object</td><td>{@link ProviderUnavailableException}</td></tr>
 * </table>
 * Messages never contain the request URL or key; vendor message text is not copied either (it can be long and change).
 */
final class AlphaVantageResponses {

    private static final String PROVIDER = AlphaVantageConfiguration.PROVIDER;

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
            if (notice.toLowerCase(Locale.ROOT).contains("premium")) {
                throw new ProviderUnavailableException(PROVIDER, function + " requires a premium plan", null);
            }
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
