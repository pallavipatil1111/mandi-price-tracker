package com.mandi.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceAnalyticsTest {

    /** Monday 5 Oct 2026. */
    private static final LocalDate MON = LocalDate.of(2026, 10, 5);

    private static PricePoint p(LocalDate date, int modal) {
        return new PricePoint(date, modal - 100, modal + 100, modal);
    }

    /** One point per calendar day starting {@code start}, with the given modal prices. */
    private static List<PricePoint> daily(LocalDate start, int... modals) {
        List<PricePoint> out = new ArrayList<>();
        for (int i = 0; i < modals.length; i++) {
            out.add(p(start.plusDays(i), modals[i]));
        }
        return out;
    }

    @Test
    void movingAverageStartsOnlyWhenTheWindowIsFull() {
        List<Integer> ma = PriceAnalytics.movingAverage(daily(MON, 100, 200, 300, 400, 500), 3);
        assertThat(ma).containsExactly(null, null, 200, 300, 400);
        assertThat(PriceAnalytics.movingAverage(daily(MON, 101, 102), 1)).containsExactly(101, 102);
        assertThat(PriceAnalytics.movingAverage(daily(MON, 100, 101), 2)).containsExactly(null, 101); // 100.5 rounds up
    }

    @Test
    void percentageChangeIsRoundedToOneDecimal() {
        assertThat(PriceAnalytics.pctChange(2000, 2150)).isEqualTo(7.5);
        assertThat(PriceAnalytics.pctChange(3000, 2900)).isEqualTo(-3.3);
        assertThatThrownBy(() -> PriceAnalytics.pctChange(0, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dayChangeComparesWithThePreviousTradingDayAcrossASundayClosure() {
        // Saturday 10 Oct, market closed Sunday, then Monday 12 Oct
        List<PricePoint> pts = List.of(p(MON.plusDays(5), 2000), p(MON.plusDays(7), 2100));
        SeriesStats s = PriceAnalytics.stats(pts);
        assertThat(s.latestDate()).isEqualTo(MON.plusDays(7));
        assertThat(s.dayChange()).isEqualTo(100);
        assertThat(s.dayChangePct()).isEqualTo(5.0);
    }

    @Test
    void weekChangeUsesTheLastTradingDayAtLeastSevenDaysEarlier() {
        List<PricePoint> pts = new ArrayList<>(List.of(p(MON, 1000), p(MON.plusDays(1), 1200)));
        pts.add(p(MON.plusDays(9), 1500)); // 9 days after Monday: the week-ago reference is Tuesday (1200)
        SeriesStats s = PriceAnalytics.stats(pts);
        assertThat(s.weekChange()).isEqualTo(300);
        assertThat(s.weekChangePct()).isEqualTo(25.0);

        SeriesStats young = PriceAnalytics.stats(daily(MON, 1000, 1100));
        assertThat(young.weekChange()).isNull();
        assertThat(young.weekChangePct()).isNull();
    }

    @Test
    void thirtyDayRangeIgnoresOlderPrices() {
        List<PricePoint> pts = new ArrayList<>();
        pts.add(p(MON.minusDays(40), 9000)); // outside the window
        pts.addAll(daily(MON, 2000, 2400, 2200));
        SeriesStats s = PriceAnalytics.stats(pts);
        assertThat(s.low30()).isEqualTo(2000);
        assertThat(s.high30()).isEqualTo(2400);
        assertThat(s.avg30()).isEqualTo(2200);
        assertThat(s.tradingDays30()).isEqualTo(3);
    }

    @Test
    void trendFollowsTheFourteenDaySlope() {
        int[] rising = new int[14];
        int[] flat = new int[14];
        int[] falling = new int[14];
        for (int i = 0; i < 14; i++) {
            rising[i] = 2000 + 20 * i;   // +₹20/day, +13% over the window
            flat[i] = 2000 + (i % 2) * 10;
            falling[i] = 4000 - 25 * i;
        }
        SeriesStats up = PriceAnalytics.stats(daily(MON, rising));
        assertThat(up.slopePerDay()).isEqualTo(20.0);
        assertThat(up.trend()).isEqualTo(Trend.RISING);
        assertThat(PriceAnalytics.stats(daily(MON, flat)).trend()).isEqualTo(Trend.STEADY);
        assertThat(PriceAnalytics.stats(daily(MON, falling)).trend()).isEqualTo(Trend.FALLING);
    }

    @Test
    void slopeUsesCalendarDaysNotRowNumbers() {
        // Same prices, but the second half arrives after a 5-day closure: the slope per day is lower.
        List<PricePoint> gap = List.of(p(MON, 1000), p(MON.plusDays(1), 1010), p(MON.plusDays(7), 1020), p(MON.plusDays(8), 1030));
        assertThat(PriceAnalytics.slopePerDay(gap)).isLessThan(PriceAnalytics.slopePerDay(daily(MON, 1000, 1010, 1020, 1030)));
    }

    @Test
    void volatilityIsZeroForAFlatSeriesAndMatchesAHandCalculation() {
        assertThat(PriceAnalytics.volatility(daily(MON, 2000, 2000, 2000, 2000))).isEqualTo(0.0);
        // changes: +10%, -10%, +10% -> mean 3.33, population sd = 9.43
        double v = PriceAnalytics.volatility(daily(MON, 1000, 1100, 990, 1089));
        assertThat(v).isCloseTo(9.43, within(0.01));
    }

    @Test
    void aSinglePriceHasNoChanges() {
        SeriesStats s = PriceAnalytics.stats(List.of(p(MON, 2500)));
        assertThat(s.dayChange()).isNull();
        assertThat(s.weekChange()).isNull();
        assertThat(s.low30()).isEqualTo(2500);
        assertThat(s.volatilityPct()).isEqualTo(0.0);
        assertThat(s.trend()).isEqualTo(Trend.STEADY);
        assertThatThrownBy(() -> PriceAnalytics.stats(Arrays.asList())).isInstanceOf(IllegalArgumentException.class);
    }
}
