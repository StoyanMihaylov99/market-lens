package com.service.market_lens.marketdata;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static com.service.market_lens.marketdata.MarketDataTestData.indexSnapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IndexSnapshotTest {

    private static final DailyClose CLOSE = new DailyClose(LocalDate.of(2026, 11, 18), new BigDecimal("6650.10"));

    @Test
    void historyIsDefensivelyCopied() {
        var history = new ArrayList<>(List.of(CLOSE));
        var snapshot = new IndexSnapshot("^GSPC", "S&P 500", CLOSE.close(), null, null, null, history);

        history.clear();

        assertThat(snapshot.history()).containsExactly(CLOSE);
    }

    @Test
    void historyIsUnmodifiable() {
        var snapshot = indexSnapshot();

        assertThatThrownBy(() -> snapshot.history().add(CLOSE))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void optionalMarketFieldsMayBeMissing() {
        var snapshot = new IndexSnapshot("^GSPC", null, CLOSE.close(), null, null, null, List.of());

        assertThat(snapshot.changePercent()).isNull();
        assertThat(snapshot.history()).isEmpty();
    }

    @Test
    void rejectsMissingRequiredFields() {
        assertThatNullPointerException()
                .isThrownBy(() -> new IndexSnapshot(null, null, CLOSE.close(), null, null, null, List.of()))
                .withMessageContaining("symbol");
        assertThatNullPointerException()
                .isThrownBy(() -> new IndexSnapshot("^GSPC", null, CLOSE.close(), null, null, null, null))
                .withMessageContaining("history");
        assertThatNullPointerException()
                .isThrownBy(() -> new DailyClose(LocalDate.of(2026, 11, 18), null))
                .withMessageContaining("close");
    }
}
