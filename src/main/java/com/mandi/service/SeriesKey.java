package com.mandi.service;

/** Identifies a price series: one commodity in one market. */
public record SeriesKey(Long commodityId, Long marketId) {
}
