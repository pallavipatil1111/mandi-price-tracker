package com.mandi.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mandi.core.PricePoint;
import com.mandi.domain.Commodity;
import com.mandi.domain.Market;
import com.mandi.repository.BoardRow;
import com.mandi.repository.CommodityRepository;
import com.mandi.repository.DailyRow;
import com.mandi.repository.ImportRunRepository;
import com.mandi.repository.MarketRepository;
import com.mandi.repository.PriceRepository;
import com.mandi.repository.SeriesRef;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Price series held in memory behind mocked repositories (the queries' grouping is done here by hand). */
final class Fixture {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-14T14:00:00Z"), ZoneId.of("Asia/Kolkata"));
    static final LocalDate TODAY = LocalDate.of(2026, 10, 14);

    final PriceRepository prices = mock(PriceRepository.class);
    final CommodityRepository commodities = mock(CommodityRepository.class);
    final MarketRepository markets = mock(MarketRepository.class);
    final ImportRunRepository runs = mock(ImportRunRepository.class);
    final Map<SeriesKey, List<PricePoint>> series = new LinkedHashMap<>();

    final Commodity onion = commodity(1L, "Onion", "Vegetables");
    final Commodity tomato = commodity(2L, "Tomato", "Vegetables");
    final Market lasalgaon = market(10L, "Nashik", "Lasalgaon");
    final Market pune = market(11L, "Pune", "Pune");
    final Market solapur = market(12L, "Solapur", "Solapur");

    Fixture() {
        for (Commodity c : List.of(onion, tomato)) {
            when(commodities.findById(c.getId())).thenReturn(Optional.of(c));
        }
        for (Market m : List.of(lasalgaon, pune, solapur)) {
            when(markets.findById(m.getId())).thenReturn(Optional.of(m));
        }
        when(runs.findFirstByStatusNotOrderByStartedAtDescIdDesc(any())).thenReturn(Optional.empty());
        when(prices.latestDate()).thenAnswer(inv -> series.values().stream().filter(l -> !l.isEmpty())
                .map(l -> l.get(l.size() - 1).date()).max(LocalDate::compareTo).orElse(null));
        when(prices.latestDate(any(), any())).thenAnswer(inv -> {
            List<PricePoint> l = series.get(new SeriesKey(inv.getArgument(0), inv.getArgument(1)));
            return l == null || l.isEmpty() ? null : l.get(l.size() - 1).date();
        });
        when(prices.series(any(), any(), any())).thenAnswer(inv -> {
            LocalDate from = inv.getArgument(2);
            return series.getOrDefault(new SeriesKey(inv.getArgument(0), inv.getArgument(1)), List.of()).stream()
                    .filter(p -> !p.date().isBefore(from))
                    .map(p -> new DailyRow(p.date(), p.min(), p.max(), (double) p.modal())).toList();
        });
        when(prices.board(any())).thenAnswer(inv -> board(null, inv.getArgument(0)));
        when(prices.boardForCommodity(any(), any())).thenAnswer(inv -> board(inv.getArgument(0), inv.getArgument(1)));
        when(prices.seriesIndex()).thenAnswer(inv -> {
            List<SeriesRef> out = new ArrayList<>();
            series.forEach((k, l) -> {
                Commodity c = byId(k.commodityId());
                Market m = marketById(k.marketId());
                out.add(new SeriesRef(c.getId(), c.getName(), c.getCategory(), m.getId(), m.getName(), m.getDistrict(),
                        m.getState(), l.get(0).date(), l.get(l.size() - 1).date(), (long) l.size()));
            });
            return out;
        });
    }

    private List<BoardRow> board(Long commodityId, LocalDate from) {
        List<BoardRow> out = new ArrayList<>();
        series.forEach((k, l) -> {
            if (commodityId != null && !commodityId.equals(k.commodityId())) {
                return;
            }
            Commodity c = byId(k.commodityId());
            Market m = marketById(k.marketId());
            for (PricePoint p : l) {
                if (!p.date().isBefore(from)) {
                    out.add(new BoardRow(c.getId(), c.getName(), c.getCategory(), m.getId(), m.getName(), m.getDistrict(),
                            m.getState(), p.date(), p.min(), p.max(), (double) p.modal()));
                }
            }
        });
        return out;
    }

    /** Adds trading days ending {@code last}: modal prices oldest first, one per day (no Sundays skipped). */
    void prices(Commodity c, Market m, LocalDate last, int... modals) {
        List<PricePoint> l = series.computeIfAbsent(new SeriesKey(c.getId(), m.getId()), k -> new ArrayList<>());
        for (int i = 0; i < modals.length; i++) {
            LocalDate d = last.minusDays(modals.length - 1L - i);
            l.add(new PricePoint(d, modals[i] - 200, modals[i] + 200, modals[i]));
        }
    }

    Commodity byId(Long id) {
        return id.equals(onion.getId()) ? onion : tomato;
    }

    Market marketById(Long id) {
        return List.of(lasalgaon, pune, solapur).stream().filter(m -> m.getId().equals(id)).findFirst().orElseThrow();
    }

    PriceQueryService query() {
        return new PriceQueryService(prices, commodities, markets, runs);
    }

    static Commodity commodity(Long id, String name, String category) {
        Commodity c = new Commodity(name, category);
        c.setId(id);
        return c;
    }

    static Market market(Long id, String district, String name) {
        Market m = new Market("Maharashtra", district, name);
        m.setId(id);
        return m;
    }
}
