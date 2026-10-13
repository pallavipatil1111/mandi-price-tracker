package com.mandi.repository;

import java.time.LocalDate;

/** A {@link DailyRow} tagged with its commodity and market, for screens that cover many series at once. */
public record BoardRow(Long commodityId, String commodity, String category, Long marketId, String market,
                       String district, String state, LocalDate date, Integer min, Integer max, Double modal) {
}
