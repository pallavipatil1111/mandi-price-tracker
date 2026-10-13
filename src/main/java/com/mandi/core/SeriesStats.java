package com.mandi.core;

import java.time.LocalDate;

/**
 * Headline numbers for a series, all computed from the latest trading day backwards.
 *
 * @param dayChange       modal change since the previous trading day (markets skip Sundays and holidays)
 * @param weekChange      change since the last trading day at least 7 days earlier
 * @param volatilityPct   standard deviation of day-to-day % changes over the last 30 days
 * @param slopePerDay     least-squares trend of the modal price over the last 14 days, ₹ per day
 */
public record SeriesStats(LocalDate latestDate, int latestModal, int latestMin, int latestMax,
                          Integer dayChange, Double dayChangePct, Integer weekChange, Double weekChangePct,
                          int low30, int high30, int avg30, double volatilityPct, double slopePerDay, Trend trend,
                          int tradingDays30) {
}
