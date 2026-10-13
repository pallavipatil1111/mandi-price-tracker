package com.mandi.ingest;

import com.mandi.domain.ImportTrigger;
import com.mandi.service.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Pulls the day's prices on a cron schedule (evening IST by default). */
@Component
public class ImportScheduler {

    private static final Logger log = LoggerFactory.getLogger(ImportScheduler.class);

    private final ImportService importService;
    private final boolean enabled;

    public ImportScheduler(ImportService importService, @Value("${app.import.enabled:true}") boolean enabled) {
        this.importService = importService;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${app.import.cron}", zone = "${app.zone}")
    public void importDaily() {
        if (!enabled) {
            return;
        }
        try {
            var run = importService.importToday(ImportTrigger.SCHEDULED);
            log.info("Scheduled import {}: {} fetched, {} new, {} updated, {} rejected, {} alerts", run.getStatus(),
                    run.getFetched(), run.getInserted(), run.getUpdated(), run.getRejected(), run.getAlertsFired());
        } catch (ApiException busy) {
            log.info("Skipped scheduled import: {}", busy.getMessage());
        }
    }
}
