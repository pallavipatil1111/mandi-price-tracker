package com.mandi.core;

import java.time.LocalDate;

/** One trading day of a series (one commodity in one market), in ₹ per quintal. */
public record PricePoint(LocalDate date, int min, int max, int modal) {
}
