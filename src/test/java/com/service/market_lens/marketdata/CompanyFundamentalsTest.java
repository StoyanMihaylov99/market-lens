package com.service.market_lens.marketdata;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.service.market_lens.marketdata.MarketDataTestData.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyFundamentalsTest {

    @Test
    void statementListsAreDefensivelyCopied() {
        var incomeStatements = new ArrayList<>(List.of(incomeStatement(LATEST_PERIOD_END, FiscalPeriod.Q3)));
        var fundamentals = new CompanyFundamentals(profile(), quote(), incomeStatements, List.of(), List.of(),
                FETCHED_AT);

        incomeStatements.add(incomeStatement(PREVIOUS_PERIOD_END, FiscalPeriod.Q2));

        assertThat(fundamentals.incomeStatements()).hasSize(1);
    }

    @Test
    void statementListsAreUnmodifiable() {
        var fundamentals = fundamentals();

        assertThatThrownBy(() -> fundamentals.balanceSheets().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyStatementListsAreAllowed() {
        // A newly listed company may have no filings yet; that's missing data, not an error.
        var fundamentals = new CompanyFundamentals(profile(), quote(), List.of(), List.of(), List.of(), FETCHED_AT);

        assertThat(fundamentals.incomeStatements()).isEmpty();
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CompanyFundamentals(null, quote(), List.of(), List.of(), List.of(), FETCHED_AT))
                .withMessageContaining("profile");
        assertThatNullPointerException()
                .isThrownBy(() -> new CompanyFundamentals(profile(), quote(), List.of(), List.of(), List.of(), null))
                .withMessageContaining("fetchedAt");
    }

    @Test
    void rejectsProfileAndQuoteForDifferentTickers() {
        var msftQuote = new Quote("MSFT", quote().price(), null, null, FETCHED_AT);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new CompanyFundamentals(profile(), msftQuote, List.of(), List.of(), List.of(),
                        FETCHED_AT))
                .withMessageContaining("AAPL").withMessageContaining("MSFT");
    }
}
