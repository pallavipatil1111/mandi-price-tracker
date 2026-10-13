package com.mandi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mandi.core.Trend;
import com.mandi.web.dto.PriceViews;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PriceQueryServiceTest {

    private Fixture f;
    private PriceQueryService service;

    @BeforeEach
    void setUp() {
        f = new Fixture();
        service = f.query();
    }

    private static int[] ramp(int start, int step, int n) {
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = start + step * i;
        }
        return out;
    }

    @Test
    void seriesShowsTheRequestedWindowWithAWarmedUpMovingAverage() {
        f.prices(f.onion, f.lasalgaon, Fixture.TODAY, ramp(2000, 10, 60));

        PriceViews.Series s = service.series(f.onion.getId(), f.lasalgaon.getId(), 7);

        assertThat(s.points()).hasSize(7);
        assertThat(s.points().get(0).date()).isEqualTo(Fixture.TODAY.minusDays(6));
        // the first shown day already has a 7-day average, thanks to the extra history fetched before it
        assertThat(s.points().get(0).ma7()).isEqualTo(s.points().get(0).modal() - 30);
        assertThat(s.commodity()).isEqualTo("Onion");
        assertThat(s.market()).isEqualTo("Lasalgaon");
        // statistics still cover 30 days even though the chart shows 7
        assertThat(s.stats().tradingDays30()).isEqualTo(30);
        assertThat(s.stats().latestModal()).isEqualTo(2590);
        assertThat(s.stats().weekChange()).isEqualTo(70);
        assertThat(s.stats().trend()).isEqualTo(Trend.RISING); // +₹140 in 14 days is over 3% of the ₹2,445 average
    }

    @Test
    void seriesValidatesItsInputs() {
        assertThatThrownBy(() -> service.series(f.onion.getId(), f.lasalgaon.getId(), 3))
                .isInstanceOf(ApiException.class).hasMessageContaining("between 7 and 1095");
        assertThatThrownBy(() -> service.series(f.onion.getId(), f.pune.getId(), 30))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void boardListsEverySeriesAndRanksTheWeeksMovers() {
        f.prices(f.onion, f.lasalgaon, Fixture.TODAY, ramp(2000, 50, 10));   // +350 in a week: +15.2%
        f.prices(f.onion, f.solapur, Fixture.TODAY, ramp(2000, -20, 10));    // falling: -7.1%
        f.prices(f.tomato, f.pune, Fixture.TODAY, ramp(1500, 5, 10));        // +2.3%
        f.prices(f.tomato, f.solapur, Fixture.TODAY.minusDays(9), 1500, 1500, 1500); // stopped reporting

        PriceViews.Board board = service.board();

        assertThat(board.latestDate()).isEqualTo(Fixture.TODAY);
        assertThat(board.rows()).hasSize(4);
        assertThat(board.gainers()).extracting(PriceViews.BoardRow::market).containsExactly("Lasalgaon", "Pune");
        assertThat(board.gainers().get(0).weekChangePct()).isEqualTo(16.7); // 2,100 a week ago -> 2,450
        assertThat(board.losers()).extracting(PriceViews.BoardRow::market).containsExactly("Solapur");
        PriceViews.BoardRow stale = board.rows().stream().filter(PriceViews.BoardRow::stale).findFirst().orElseThrow();
        assertThat(stale.commodity()).isEqualTo("Tomato");
        assertThat(board.rows().get(0).spark()).hasSize(10);
    }

    @Test
    void compareRanksMarketsAndReportsTheSpread() {
        f.prices(f.onion, f.lasalgaon, Fixture.TODAY, 2100, 2150);
        f.prices(f.onion, f.pune, Fixture.TODAY, 2300, 2400);
        f.prices(f.onion, f.solapur, Fixture.TODAY.minusDays(1), 1950, 2000);

        PriceViews.Compare c = service.compare(f.onion.getId());

        assertThat(c.markets()).extracting(PriceViews.BoardRow::market).containsExactly("Pune", "Lasalgaon", "Solapur");
        assertThat(c.highestMarket()).isEqualTo("Pune");
        assertThat(c.lowestMarket()).isEqualTo("Solapur");
        assertThat(c.spread()).isEqualTo(400);
        assertThat(c.spreadPct()).isEqualTo(20.0);
    }

    @Test
    void monthlyAveragesShowTheSeason() {
        f.prices(f.onion, f.lasalgaon, Fixture.TODAY, ramp(1000, 20, 45)); // 31 Aug .. 14 Oct

        List<PriceViews.Month> months = service.monthly(f.onion.getId(), f.lasalgaon.getId()).months();

        assertThat(months).extracting(m -> m.year() + "-" + m.month()).containsExactly("2026-8", "2026-9", "2026-10");
        assertThat(months.get(0).tradingDays()).isEqualTo(1);
        assertThat(months.get(1).tradingDays()).isEqualTo(30);
        assertThat(months.get(1).avgModal()).isEqualTo(1310); // 1,020 .. 1,600
        assertThat(months.get(2).high()).isEqualTo(1880);
    }

    @Test
    void catalogGroupsMarketsUnderTheirCommodity() {
        f.prices(f.onion, f.lasalgaon, Fixture.TODAY, 2000);
        f.prices(f.onion, f.pune, Fixture.TODAY, 2100);
        f.prices(f.tomato, f.pune, Fixture.TODAY, 1500);

        PriceViews.Catalog catalog = service.catalog();

        assertThat(catalog.commodities()).extracting(PriceViews.CommodityRef::name).containsExactly("Onion", "Tomato");
        assertThat(catalog.commodities().get(0).markets()).extracting(PriceViews.MarketRef::name)
                .containsExactly("Lasalgaon", "Pune");
        assertThat(catalog.categories()).containsExactly("Vegetables");
    }

    @Test
    void csvExportIsSpreadsheetSafe() {
        f.prices(f.onion, f.lasalgaon, Fixture.TODAY, ramp(2000, 10, 10));
        String csv = service.csv(f.onion.getId(), f.lasalgaon.getId(), 7);
        assertThat(csv).startsWith("date,commodity,market,district,state,min_price,max_price,modal_price,moving_avg_7\n");
        assertThat(csv).hasLineCount(8);
        assertThat(csv).contains("2026-10-14,Onion,Lasalgaon,Nashik,Maharashtra,1890,2290,2090,2060\n");
        assertThat(PriceQueryService.csvCell("Pune, Pimpri")).isEqualTo("\"Pune, Pimpri\"");
        assertThat(PriceQueryService.csvCell("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
    }
}
