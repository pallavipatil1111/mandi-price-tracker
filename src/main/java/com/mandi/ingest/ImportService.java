package com.mandi.ingest;

import com.mandi.core.CleanRecord;
import com.mandi.core.RecordValidator;
import com.mandi.domain.ImportRun;
import com.mandi.domain.ImportTrigger;
import com.mandi.repository.ImportRunRepository;
import com.mandi.service.AlertService;
import com.mandi.service.ApiException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Runs an import: fetch, validate, upsert, evaluate alerts, and record everything in an import run.
 * Only one import runs at a time; a second request (say, an admin clicking "Import now" during the
 * scheduled run) is refused instead of racing it.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);
    /** Without alerts, history is written a month per transaction instead of a day per transaction. */
    static final int BULK_CHUNK_DAYS = 31;

    private final PriceSource source;
    private final ImportWriter writer;
    private final ImportRunRepository runs;
    private final AlertService alerts;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    public ImportService(PriceSource source, ImportWriter writer, ImportRunRepository runs, AlertService alerts,
                         Clock clock) {
        this.source = source;
        this.writer = writer;
        this.runs = runs;
        this.alerts = alerts;
        this.clock = clock;
    }

    public String sourceName() {
        return source.name();
    }

    public boolean isRunning() {
        return running.get();
    }

    /** Today's prices, with alerts. Used by the scheduler and the admin "Import now" button. */
    public ImportRun importToday(ImportTrigger trigger) {
        return run(trigger, List.of(LocalDate.now(clock)), true);
    }

    public ImportRun run(ImportTrigger trigger, List<LocalDate> dates, boolean evaluateAlerts) {
        if (!running.compareAndSet(false, true)) {
            throw ApiException.conflict("An import is already running - try again in a minute");
        }
        ImportRun run = runs.save(new ImportRun(source.name(), trigger, clock.instant()));
        Map<String, Integer> reasons = new TreeMap<>();
        try {
            List<CleanRecord> pending = new ArrayList<>();
            int fetchedPending = 0;
            int rejectedPending = 0;
            for (int i = 0; i < dates.size(); i++) {
                LocalDate today = LocalDate.now(clock);
                for (var raw : source.fetch(dates.get(i))) {
                    fetchedPending++;
                    switch (RecordValidator.validate(raw, today)) {
                        case RecordValidator.Ok ok -> pending.add(ok.record());
                        case RecordValidator.Rejected bad -> {
                            rejectedPending++;
                            reasons.merge(bad.reason(), 1, Integer::sum);
                        }
                    }
                }
                boolean flush = evaluateAlerts || pending.size() > 0 && (i + 1) % BULK_CHUNK_DAYS == 0
                        || i == dates.size() - 1;
                if (flush) {
                    ImportWriter.Result result = writer.write(pending, source.name(), clock.instant());
                    int fired = evaluateAlerts ? alerts.evaluate(result.touched()) : 0;
                    run.addCounts(fetchedPending, result.inserted(), result.updated(), result.unchanged(),
                            rejectedPending, fired);
                    pending.forEach(r -> run.coverDate(r.date()));
                    pending = new ArrayList<>();
                    fetchedPending = 0;
                    rejectedPending = 0;
                }
            }
            run.finish(clock.instant(), summary(run, reasons));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            run.fail(clock.instant(), "Interrupted");
        } catch (Exception e) {
            log.warn("Import from {} failed", source.name(), e);
            run.fail(clock.instant(), e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            running.set(false);
        }
        return runs.save(run);
    }

    static String summary(ImportRun run, Map<String, Integer> reasons) {
        if (run.getFetched() == 0) {
            return "The source returned no rows (market holiday, or not published yet)";
        }
        if (reasons.isEmpty()) {
            return null;
        }
        return "Rejected " + run.getRejected() + (run.getRejected() == 1 ? " row: " : " rows: ")
                + reasons.entrySet().stream().map(e -> e.getValue() + " × " + e.getKey()).collect(Collectors.joining(", "));
    }
}
