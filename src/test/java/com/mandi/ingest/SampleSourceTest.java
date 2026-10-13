package com.mandi.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.mandi.core.CleanRecord;
import com.mandi.core.RawRecord;
import com.mandi.core.RecordValidator;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SampleSourceTest {

    private final SampleSource source = new SampleSource();

    @Test
    void theSameDateAlwaysProducesTheSameRows() {
        LocalDate day = LocalDate.of(2026, 10, 13);
        assertThat(source.fetch(day)).isEqualTo(new SampleSource().fetch(day));
        assertThat(source.fetch(day)).isEqualTo(source.fetch(day));
    }

    @Test
    void marketsAreClosedOnSundays() {
        LocalDate sunday = LocalDate.of(2026, 10, 11);
        assertThat(sunday.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(source.fetch(sunday)).isEmpty();
    }

    @Test
    void coversTwentySevenSeriesInFifteenMaharashtraMarkets() {
        Set<String> markets = new HashSet<>();
        SampleSource.SERIES.forEach(s -> markets.add(s.market()));
        assertThat(SampleSource.SERIES).hasSize(27);
        assertThat(markets).hasSize(15);
    }

    @Test
    void almostEveryRowIsValidAndTheBadOnesAreTheDeliberateQuirks() {
        LocalDate today = LocalDate.of(2026, 10, 14);
        int valid = 0;
        int rejected = 0;
        int formatted = 0;
        for (LocalDate d = today.minusDays(120); !d.isAfter(today); d = d.plusDays(1)) {
            for (RawRecord raw : source.fetch(d)) {
                if (raw.modalPrice().contains(",")) {
                    formatted++;
                }
                if (RecordValidator.validate(raw, today) instanceof RecordValidator.Ok ok) {
                    CleanRecord r = ok.record();
                    assertThat(r.min()).isLessThanOrEqualTo(r.modal());
                    assertThat(r.modal()).isLessThanOrEqualTo(r.max());
                    assertThat(r.market()).isEqualTo(r.market().trim());
                    valid++;
                } else {
                    rejected++;
                }
            }
        }
        assertThat(valid).isGreaterThan(2500);
        assertThat(rejected).isBetween(1, valid / 50);
        assertThat(formatted).isGreaterThan(20);
    }

    @Test
    void onionPeaksAfterTheMonsoonAndTomatoDuringIt() {
        assertThat(avg("Onion", "Lasalgaon", LocalDate.of(2025, 10, 15), 45))
                .isGreaterThan(avg("Onion", "Lasalgaon", LocalDate.of(2025, 4, 15), 45));
        assertThat(avg("Tomato", "Pune", LocalDate.of(2025, 7, 1), 45))
                .isGreaterThan(avg("Tomato", "Pune", LocalDate.of(2025, 1, 1), 45));
    }

    @Test
    void marketsOfOneCommodityMoveTogether() {
        // Shared shocks: Lasalgaon and Pimpalgaon onion stay within a few percent of each other.
        for (LocalDate d = LocalDate.of(2026, 3, 2); d.isBefore(LocalDate.of(2026, 3, 30)); d = d.plusDays(1)) {
            Integer a = modal(d, "Onion", "Lasalgaon");
            Integer b = modal(d, "Onion", "Pimpalgaon Baswant");
            if (a != null && b != null) {
                assertThat(Math.abs(a - b) * 100.0 / a).isLessThan(12.0);
            }
        }
    }

    private double avg(String commodity, String market, LocalDate from, int days) {
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            Integer m = modal(from.plusDays(i), commodity, market);
            if (m != null) {
                values.add(m);
            }
        }
        return values.stream().mapToInt(Integer::intValue).average().orElseThrow();
    }

    private Integer modal(LocalDate date, String commodity, String market) {
        for (RawRecord raw : source.fetch(date)) {
            if (RecordValidator.validate(raw, date) instanceof RecordValidator.Ok ok
                    && ok.record().commodity().equals(commodity) && ok.record().market().equals(market)) {
                return ok.record().modal();
            }
        }
        return null;
    }
}
