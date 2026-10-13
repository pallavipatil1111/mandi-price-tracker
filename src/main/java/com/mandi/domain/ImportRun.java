package com.mandi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** The audit record of one import: what was fetched, what changed, what was rejected and why. */
@Entity
@Table(name = "import_runs")
public class ImportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 12)
    private ImportTrigger trigger;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ImportStatus status = ImportStatus.RUNNING;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    private int fetched;
    private int inserted;
    private int updated;
    private int unchanged;
    private int rejected;

    @Column(name = "alerts_fired")
    private int alertsFired;

    @Column(name = "first_date")
    private LocalDate firstDate;

    @Column(name = "last_date")
    private LocalDate lastDate;

    @Column(length = 500)
    private String message;

    protected ImportRun() {
    }

    public ImportRun(String source, ImportTrigger trigger, Instant startedAt) {
        this.source = source;
        this.trigger = trigger;
        this.startedAt = startedAt;
    }

    public void addCounts(int fetched, int inserted, int updated, int unchanged, int rejected, int alertsFired) {
        this.fetched += fetched;
        this.inserted += inserted;
        this.updated += updated;
        this.unchanged += unchanged;
        this.rejected += rejected;
        this.alertsFired += alertsFired;
    }

    public void coverDate(LocalDate date) {
        if (firstDate == null || date.isBefore(firstDate)) {
            firstDate = date;
        }
        if (lastDate == null || date.isAfter(lastDate)) {
            lastDate = date;
        }
    }

    public void finish(Instant at, String message) {
        this.status = rejected > 0 ? ImportStatus.PARTIAL : ImportStatus.SUCCESS;
        this.finishedAt = at;
        this.message = truncate(message);
    }

    public void fail(Instant at, String message) {
        this.status = ImportStatus.FAILED;
        this.finishedAt = at;
        this.message = truncate(message);
    }

    private static String truncate(String text) {
        return text == null || text.length() <= 500 ? text : text.substring(0, 497) + "...";
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public ImportTrigger getTrigger() {
        return trigger;
    }

    public ImportStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public int getFetched() {
        return fetched;
    }

    public int getInserted() {
        return inserted;
    }

    public int getUpdated() {
        return updated;
    }

    public int getUnchanged() {
        return unchanged;
    }

    public int getRejected() {
        return rejected;
    }

    public int getAlertsFired() {
        return alertsFired;
    }

    public LocalDate getFirstDate() {
        return firstDate;
    }

    public LocalDate getLastDate() {
        return lastDate;
    }

    public String getMessage() {
        return message;
    }
}
