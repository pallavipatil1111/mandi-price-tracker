package com.mandi.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/**
 * Cleans one Agmarknet row. Government data is messy: blank fields, "1,250.00" style numbers, two date
 * formats, and the occasional modal price outside its own min–max range. Bad rows are rejected with a
 * reason (counted per import) instead of failing the whole import.
 */
public final class RecordValidator {

    /** Prices above this (₹20 lakh per quintal) are data-entry errors for any commodity sold in a mandi. */
    public static final int MAX_PRICE = 2_000_000;

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            strict("dd/MM/uuuu"), DateTimeFormatter.ISO_LOCAL_DATE, strict("dd-MM-uuuu"));

    public sealed interface Result permits Ok, Rejected {
    }

    public record Ok(CleanRecord record) implements Result {
    }

    public record Rejected(String reason) implements Result {
    }

    private RecordValidator() {
    }

    /** STRICT so that "31/02/2026" is an error instead of silently becoming 28 February. */
    private static DateTimeFormatter strict(String pattern) {
        return DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT);
    }

    public static Result validate(RawRecord raw, LocalDate today) {
        String state = clean(raw.state());
        String district = clean(raw.district());
        String market = clean(raw.market());
        String commodity = clean(raw.commodity());
        if (state.isEmpty() || district.isEmpty() || market.isEmpty() || commodity.isEmpty()) {
            return new Rejected("missing state, district, market or commodity");
        }
        String variety = clean(raw.variety());
        if (variety.isEmpty()) {
            variety = "Other";
        }
        LocalDate date = parseDate(raw.arrivalDate());
        if (date == null) {
            return new Rejected("unreadable arrival date");
        }
        if (date.isAfter(today.plusDays(1))) {
            return new Rejected("arrival date in the future");
        }
        Integer modal = parsePrice(raw.modalPrice());
        if (modal == null) {
            return new Rejected("missing or invalid modal price");
        }
        // Some markets only report a modal price; treat a missing min/max as equal to it.
        Integer min = blank(raw.minPrice()) ? modal : parsePrice(raw.minPrice());
        Integer max = blank(raw.maxPrice()) ? modal : parsePrice(raw.maxPrice());
        if (min == null || max == null) {
            return new Rejected("invalid min or max price");
        }
        if (min > max) {
            return new Rejected("min price above max price");
        }
        if (modal < min || modal > max) {
            return new Rejected("modal price outside min-max range");
        }
        return new Ok(new CleanRecord(state, district, market, commodity, variety, date, min, max, modal));
    }

    /** Trims and collapses internal whitespace ("Pune  (Pimpri) " becomes "Pune (Pimpri)"). */
    public static String clean(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    static LocalDate parseDate(String value) {
        String text = clean(value);
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(text, format);
            } catch (DateTimeParseException ignored) {
                // try the next format
            }
        }
        return null;
    }

    /** Whole rupees, rounded half-up; null if blank, not a number, zero, negative or absurdly high. */
    static Integer parsePrice(String value) {
        String text = clean(value).replace(",", "");
        if (text.isEmpty()) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(text).setScale(0, RoundingMode.HALF_UP);
            if (price.signum() <= 0 || price.compareTo(BigDecimal.valueOf(MAX_PRICE)) > 0) {
                return null;
            }
            return price.intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private static boolean blank(String value) {
        return clean(value).isEmpty();
    }
}
