package com.mandi.ingest;

import com.mandi.core.RawRecord;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/** Where prices come from: the live data.gov.in API or the offline sample generator. */
public interface PriceSource {

    /** Short name stored with every price row and import run. */
    String name();

    /**
     * Rows for one trading day. The live API only serves the latest published prices, so it ignores
     * {@code date}; each row carries its own arrival date either way.
     */
    List<RawRecord> fetch(LocalDate date) throws IOException, InterruptedException;

    /** True if the source can produce any date on request (used to backfill demo history). */
    default boolean supportsHistory() {
        return false;
    }
}
