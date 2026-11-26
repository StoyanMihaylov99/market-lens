package com.service.market_lens.api;

/** Ticker format accepted at the API edge. Upper-casing happens in the controller before the provider is called. */
final class Tickers {

    /**
     * Letters, digits, dot and dash, 1–12 characters, starting with a letter or digit: {@code AAPL}, {@code BRK.B},
     * {@code 0700.HK}, {@code RDS-A}. Lower case is accepted and normalised. Punctuation-only input ({@code ---})
     * is rejected here instead of costing a provider request.
     */
    static final String PATTERN = "[A-Za-z0-9][A-Za-z0-9.\\-]{0,11}";

    private Tickers() {
    }
}
