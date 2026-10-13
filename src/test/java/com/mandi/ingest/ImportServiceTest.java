package com.mandi.ingest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mandi.core.RawRecord;
import com.mandi.domain.ImportRun;
import com.mandi.domain.ImportStatus;
import com.mandi.domain.ImportTrigger;
import com.mandi.domain.Price;
import com.mandi.service.AlertService;
import com.mandi.service.ApiException;
import com.mandi.service.SeriesKey;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImportServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T14:00:00Z"), ZoneId.of("Asia/Kolkata"));
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    /** A scriptable source: rows per date, or an exception. */
    static final class FakeSource implements PriceSource {
        final Map<LocalDate, List<RawRecord>> rows = new HashMap<>();
        Exception failure;
        Runnable duringFetch = () -> { };

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public List<RawRecord> fetch(LocalDate date) throws IOException, InterruptedException {
            duringFetch.run();
            if (failure instanceof IOException io) {
                throw io;
            }
            if (failure instanceof RuntimeException re) {
                throw re;
            }
            return rows.getOrDefault(date, List.of());
        }
    }

    /** Records which series the alert service was asked to check. */
    static final class RecordingAlerts extends AlertService {
        final List<Set<SeriesKey>> calls = new ArrayList<>();
        int toFire;

        RecordingAlerts() {
            super(null, null, null, CLOCK);
        }

        @Override
        public int evaluate(Collection<SeriesKey> touched) {
            calls.add(Set.copyOf(touched));
            return touched.isEmpty() ? 0 : toFire;
        }
    }

    private InMemoryStore store;
    private FakeSource source;
    private RecordingAlerts alerts;
    private ImportService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryStore();
        source = new FakeSource();
        alerts = new RecordingAlerts();
        service = new ImportService(source, new ImportWriter(store.prices, store.markets, store.commodities), store.runs,
                alerts, CLOCK);
    }

    private static RawRecord onion(String market, String date, String modal) {
        return new RawRecord("Maharashtra", "Nashik", market, "Onion", "Red", date, "1500", "3000", modal);
    }

    @Test
    void importsNewRowsCreatingMarketsAndCommoditiesOnce() {
        source.rows.put(TODAY, List.of(onion("Lasalgaon", "14/10/2026", "2150"), onion("Pimpalgaon", "14/10/2026", "2200"),
                new RawRecord("Maharashtra", "Pune", "Pune", "Tomato", "Hybrid", "14/10/2026", "1200", "2000", "1600")));

        ImportRun run = service.importToday(ImportTrigger.MANUAL);

        assertThat(run.getStatus()).isEqualTo(ImportStatus.SUCCESS);
        assertThat(run.getFetched()).isEqualTo(3);
        assertThat(run.getInserted()).isEqualTo(3);
        assertThat(run.getFirstDate()).isEqualTo(TODAY);
        assertThat(run.getFinishedAt()).isEqualTo(CLOCK.instant());
        assertThat(store.marketRows).hasSize(3);
        assertThat(store.commodityRows).extracting(c -> c.getName() + "/" + c.getCategory())
                .containsExactly("Onion/Vegetables", "Tomato/Vegetables");
        assertThat(alerts.calls).hasSize(1);
        assertThat(alerts.calls.get(0)).hasSize(3);
    }

    @Test
    void reImportingTheSameDayUpdatesChangedRowsAndSkipsUnchangedOnes() {
        source.rows.put(TODAY, List.of(onion("Lasalgaon", "14/10/2026", "2150"), onion("Pimpalgaon", "14/10/2026", "2200")));
        service.importToday(ImportTrigger.SCHEDULED);

        // Agmarknet revised one market's price later in the evening
        source.rows.put(TODAY, List.of(onion("Lasalgaon", "14/10/2026", "2180"), onion("Pimpalgaon", "14/10/2026", "2200")));
        ImportRun second = service.importToday(ImportTrigger.SCHEDULED);

        assertThat(second.getInserted()).isEqualTo(0);
        assertThat(second.getUpdated()).isEqualTo(1);
        assertThat(second.getUnchanged()).isEqualTo(1);
        assertThat(store.priceRows).hasSize(2);
        assertThat(store.priceRows).extracting(Price::getModalPrice).containsExactly(2180, 2200);
        // only the revised series is re-checked for alerts
        assertThat(alerts.calls.get(1)).hasSize(1);
    }

    @Test
    void badRowsAreRejectedWithReasonsWhileGoodRowsStillLoad() {
        source.rows.put(TODAY, List.of(onion("Lasalgaon", "14/10/2026", "2150"), onion("Pune", "14/10/2026", "3500"),
                onion("Solapur", "14/10/2026", ""), onion("Satara", "14/10/2026", "3400")));

        ImportRun run = service.importToday(ImportTrigger.MANUAL);

        assertThat(run.getStatus()).isEqualTo(ImportStatus.PARTIAL);
        assertThat(run.getInserted()).isEqualTo(1);
        assertThat(run.getRejected()).isEqualTo(3);
        assertThat(run.getMessage()).isEqualTo(
                "Rejected 3 rows: 1 × missing or invalid modal price, 2 × modal price outside min-max range");
    }

    @Test
    void aSourceFailureIsRecordedAsAFailedRun() {
        source.failure = new IOException("data.gov.in returned HTTP 502");

        ImportRun run = service.importToday(ImportTrigger.SCHEDULED);

        assertThat(run.getStatus()).isEqualTo(ImportStatus.FAILED);
        assertThat(run.getMessage()).isEqualTo("IOException: data.gov.in returned HTTP 502");
        assertThat(store.runRows).hasSize(1);
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void anEmptyDayIsASuccessWithAnExplanation() {
        ImportRun run = service.importToday(ImportTrigger.SCHEDULED);
        assertThat(run.getStatus()).isEqualTo(ImportStatus.SUCCESS);
        assertThat(run.getMessage()).contains("no rows");
    }

    @Test
    void onlyOneImportRunsAtATime() {
        source.duringFetch = () -> assertThatThrownBy(() -> service.importToday(ImportTrigger.MANUAL))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already running");
        source.rows.put(TODAY, List.of(onion("Lasalgaon", "14/10/2026", "2150")));

        ImportRun run = service.importToday(ImportTrigger.SCHEDULED);

        assertThat(run.getStatus()).isEqualTo(ImportStatus.SUCCESS);
        assertThat(store.runRows).hasSize(1);
        assertThat(service.isRunning()).isFalse();
    }

    @Test
    void historyWithoutAlertsIsWrittenInMonthlyBatches() {
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 70; i >= 1; i--) {
            LocalDate d = TODAY.minusDays(i);
            dates.add(d);
            source.rows.put(d, List.of(onion("Lasalgaon", d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")), "2000")));
        }

        ImportRun run = service.run(ImportTrigger.BACKFILL, dates, false);

        assertThat(run.getInserted()).isEqualTo(70);
        assertThat(run.getFirstDate()).isEqualTo(TODAY.minusDays(70));
        assertThat(run.getLastDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(store.findForDatesCalls).isEqualTo(3); // 31 + 31 + 8 days
        assertThat(alerts.calls).isEmpty();
        assertThat(run.getAlertsFired()).isZero();
    }

    @Test
    void firedAlertsAreCounted() {
        alerts.toFire = 2;
        source.rows.put(TODAY, List.of(onion("Lasalgaon", "14/10/2026", "2150")));
        assertThat(service.importToday(ImportTrigger.SCHEDULED).getAlertsFired()).isEqualTo(2);
    }
}
