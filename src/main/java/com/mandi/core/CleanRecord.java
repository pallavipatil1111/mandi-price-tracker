package com.mandi.core;

import java.time.LocalDate;

/** A validated price row: names normalised, prices in whole rupees per quintal, min &lt;= modal &lt;= max. */
public record CleanRecord(String state, String district, String market, String commodity, String variety,
                          LocalDate date, int min, int max, int modal) {

    /** Identifies the database row: re-importing the same key updates instead of duplicating. */
    public String key() {
        return String.join("|", state, district, market, commodity, variety, date.toString());
    }
}
