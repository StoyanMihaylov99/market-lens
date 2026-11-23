package com.service.market_lens.marketdata;

import java.util.List;

/**
 * Port for market data: the only way the rest of the application obtains fundamentals, earnings and index data.
 * Implementations (adapters) live in vendor sub-packages, e.g. {@code marketdata.alphavantage}; exactly one is active,
 * selected by {@code marketlens.market-data.provider}.
 * <p>
 * Contract for every implementation:
 * <ul>
 *   <li>Tickers and symbols are passed as given; validation and normalisation happen at the API edge.</li>
 *   <li>Unknown ticker or symbol → {@link TickerNotFoundException}. A known ticker that simply has no data of the
 *       requested kind → empty list, not an exception.</li>
 *   <li>Vendor outage, timeout or unreadable response → {@link ProviderUnavailableException}.</li>
 *   <li>Quota exhausted → {@link ProviderRateLimitedException}.</li>
 *   <li>Returned lists are immutable and, unless stated otherwise, newest first.</li>
 * </ul>
 */
public interface MarketDataProvider {

    /**
     * Profile, latest quote and recent quarterly statements (newest first) for one company.
     *
     * @throws TickerNotFoundException if the provider does not know {@code ticker}
     */
    CompanyFundamentals getFundamentals(String ticker);

    /**
     * The most recent <b>already reported</b> earnings releases, newest first. Scheduled future releases
     * (which have no actual figures yet) are excluded.
     *
     * @param count maximum number of reports to return; must be at least 1
     * @return at most {@code count} reports; empty if the company has not reported yet
     * @throws IllegalArgumentException if {@code count < 1}
     * @throws TickerNotFoundException  if the provider does not know {@code ticker}
     */
    List<EarningsReport> getRecentEarnings(String ticker, int count);

    /**
     * Current snapshot of each requested index, in the <b>same order as {@code symbols}</b>.
     * <p>
     * Index symbols come from our configuration, not from users, so an unknown one is a configuration error
     * and fails loudly instead of being skipped.
     *
     * @throws TickerNotFoundException if any symbol is unknown to the provider
     */
    List<IndexSnapshot> getIndices(List<String> symbols);
}
