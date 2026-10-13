package com.mandi.web.dto;

import com.mandi.domain.ImportRun;
import com.mandi.domain.ImportStatus;
import com.mandi.domain.ImportTrigger;
import java.time.Instant;
import java.time.LocalDate;

public record ImportRunView(Long id, String source, ImportTrigger trigger, ImportStatus status, Instant startedAt,
                            Instant finishedAt, int fetched, int inserted, int updated, int unchanged, int rejected,
                            int alertsFired, LocalDate firstDate, LocalDate lastDate, String message) {

    public static ImportRunView of(ImportRun r) {
        return new ImportRunView(r.getId(), r.getSource(), r.getTrigger(), r.getStatus(), r.getStartedAt(),
                r.getFinishedAt(), r.getFetched(), r.getInserted(), r.getUpdated(), r.getUnchanged(), r.getRejected(),
                r.getAlertsFired(), r.getFirstDate(), r.getLastDate(), r.getMessage());
    }
}
