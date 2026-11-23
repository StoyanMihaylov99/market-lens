package com.service.market_lens.marketdata;

/** The provider does not know the requested ticker or index symbol. Maps to HTTP 404. */
public final class TickerNotFoundException extends MarketDataException {

    private final String ticker;

    public TickerNotFoundException(String ticker) {
        super("Ticker not found: " + ticker, null);
        this.ticker = ticker;
    }

    public String ticker() {
        return ticker;
    }
}
