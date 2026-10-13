package com.mandi.repository;

import java.time.LocalDate;

/**
 * One day of a series. A market can report several varieties of the same commodity on one day, so the
 * database groups them: the widest min–max range and the average modal price.
 */
public record DailyRow(LocalDate date, Integer min, Integer max, Double modal) {
}
