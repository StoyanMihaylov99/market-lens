package com.service.market_lens.marketdata.alphavantage;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.support.HttpRequestWrapper;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Adds Alpha Vantage's {@code apikey} query parameter to every outgoing request.
 * <p>
 * Why an interceptor instead of {@code .queryParam("apikey", key)} at each call site:
 * <ul>
 *   <li>the key is added at the last moment, so the URI our code builds, and that {@code RestClient} puts into
 *       its exception messages, never contains it;</li>
 *   <li>call sites can't forget it, and there is exactly one place that touches the secret.</li>
 * </ul>
 * A missing key fails the <em>request</em>, not startup, so the app and CI run without secrets.
 */
final class ApiKeyInterceptor implements ClientHttpRequestInterceptor {

    static final String PARAM = "apikey";

    private final @Nullable String apiKey;

    ApiKeyInterceptor(@Nullable String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Alpha Vantage API key is not configured; set MARKET_DATA_API_KEY (see .env.example)");
        }
        URI withKey = UriComponentsBuilder.fromUri(request.getURI())
                // encode(), not encodeQueryParam(): the latter leaves '+' as-is, which servers decode as a space
                .replaceQueryParam(PARAM, UriUtils.encode(apiKey, StandardCharsets.UTF_8))
                .build(true)   // true = components are already encoded; don't encode twice
                .toUri();
        return execution.execute(new HttpRequestWrapper(request) {
            @Override
            public URI getURI() {
                return withKey;
            }
        }, body);
    }
}
