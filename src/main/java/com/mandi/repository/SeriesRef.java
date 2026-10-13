package com.mandi.repository;

import java.time.LocalDate;

/** A commodity-in-a-market pair that has prices, with its date range. */
public record SeriesRef(Long commodityId, String commodity, String category, Long marketId, String market,
                        String district, String state, LocalDate firstDate, LocalDate lastDate, Long days) {
}
