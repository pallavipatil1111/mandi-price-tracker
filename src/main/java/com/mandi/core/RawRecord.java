package com.mandi.core;

/** One row exactly as a price source delivered it: every field is still untrusted text. */
public record RawRecord(String state, String district, String market, String commodity, String variety,
                        String arrivalDate, String minPrice, String maxPrice, String modalPrice) {
}
