package com.mandi.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecordValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    private static RawRecord row(String date, String min, String max, String modal) {
        return new RawRecord("Maharashtra", "Nashik", "Lasalgaon", "Onion", "Red", date, min, max, modal);
    }

    private static CleanRecord ok(RawRecord raw) {
        RecordValidator.Result result = RecordValidator.validate(raw, TODAY);
        assertThat(result).isInstanceOf(RecordValidator.Ok.class);
        return ((RecordValidator.Ok) result).record();
    }

    private static String rejected(RawRecord raw) {
        RecordValidator.Result result = RecordValidator.validate(raw, TODAY);
        assertThat(result).isInstanceOf(RecordValidator.Rejected.class);
        return ((RecordValidator.Rejected) result).reason();
    }

    @Test
    void parsesAgmarknetDatesAndIndianNumberFormats() {
        CleanRecord r = ok(row("13/10/2026", "1,800", "2,450.00", "2150.4"));
        assertThat(r.date()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(r.min()).isEqualTo(1800);
        assertThat(r.max()).isEqualTo(2450);
        assertThat(r.modal()).isEqualTo(2150);
        assertThat(ok(row("2026-10-13", "1800", "2450", "2150.5")).modal()).isEqualTo(2151);
    }

    @Test
    void normalisesNamesAndDefaultsTheVariety() {
        CleanRecord r = ok(new RawRecord(" Maharashtra ", "Pune", "  Pune   (Pimpri) ", "Onion", "  ", "13/10/2026",
                "1800", "2400", "2100"));
        assertThat(r.market()).isEqualTo("Pune (Pimpri)");
        assertThat(r.state()).isEqualTo("Maharashtra");
        assertThat(r.variety()).isEqualTo("Other");
        assertThat(r.key()).isEqualTo("Maharashtra|Pune|Pune (Pimpri)|Onion|Other|2026-10-13");
    }

    @Test
    void missingMinOrMaxFallsBackToTheModalPrice() {
        CleanRecord r = ok(row("13/10/2026", "", null, "2100"));
        assertThat(r.min()).isEqualTo(2100);
        assertThat(r.max()).isEqualTo(2100);
    }

    @Test
    void rejectsInconsistentPriceRanges() {
        assertThat(rejected(row("13/10/2026", "1800", "2400", "2600"))).isEqualTo("modal price outside min-max range");
        assertThat(rejected(row("13/10/2026", "2500", "2400", "2450"))).isEqualTo("min price above max price");
    }

    @Test
    void rejectsMissingZeroNegativeAndAbsurdPrices() {
        for (String bad : List.of("", "NA", "0", "-50", "2500000", "12abc")) {
            assertThat(rejected(row("13/10/2026", "", "", bad))).isEqualTo("missing or invalid modal price");
        }
        assertThat(rejected(row("13/10/2026", "x", "2400", "2100"))).isEqualTo("invalid min or max price");
    }

    @Test
    void rejectsUnreadableAndFutureDatesButAllowsTomorrowForTimeZones() {
        assertThat(rejected(row("31/02/2026", "1800", "2400", "2100"))).isEqualTo("unreadable arrival date");
        assertThat(rejected(row("Oct 13", "1800", "2400", "2100"))).isEqualTo("unreadable arrival date");
        assertThat(rejected(row("16/10/2026", "1800", "2400", "2100"))).isEqualTo("arrival date in the future");
        assertThat(ok(row("15/10/2026", "1800", "2400", "2100")).date()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void rejectsRowsWithoutLocationOrCommodity() {
        RawRecord noMarket = new RawRecord("Maharashtra", "Nashik", " ", "Onion", "Red", "13/10/2026", "1", "2", "2");
        assertThat(rejected(noMarket)).isEqualTo("missing state, district, market or commodity");
    }
}
