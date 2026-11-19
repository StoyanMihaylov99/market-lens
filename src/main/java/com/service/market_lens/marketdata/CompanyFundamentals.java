package com.service.market_lens.marketdata;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Everything the metrics feature needs about one company, fetched together.
 * <p>
 * The statement lists are <b>newest first</b> and may be empty (e.g. a newly listed company).
 *
 * @param fetchedAt when this data was retrieved from the provider; surfaced to clients as the data timestamp
 */
public record CompanyFundamentals(
        CompanyProfile profile,
        Quote quote,
        List<IncomeStatement> incomeStatements,
        List<BalanceSheet> balanceSheets,
        List<CashFlowStatement> cashFlowStatements,
        Instant fetchedAt) {

    public CompanyFundamentals {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(quote, "quote");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        if (!profile.ticker().equals(quote.ticker())) {
            throw new IllegalArgumentException(
                    "profile ticker " + profile.ticker() + " does not match quote ticker " + quote.ticker());
        }
        incomeStatements = List.copyOf(incomeStatements);
        balanceSheets = List.copyOf(balanceSheets);
        cashFlowStatements = List.copyOf(cashFlowStatements);
    }
}
