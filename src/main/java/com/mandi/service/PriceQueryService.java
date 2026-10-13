package com.mandi.service;

import com.mandi.core.PriceAnalytics;
import com.mandi.core.PricePoint;
import com.mandi.core.SeriesStats;
import com.mandi.domain.Commodity;
import com.mandi.domain.ImportStatus;
import com.mandi.domain.Market;
import com.mandi.repository.BoardRow;
import com.mandi.repository.CommodityRepository;
import com.mandi.repository.DailyRow;
import com.mandi.repository.ImportRunRepository;
import com.mandi.repository.MarketRepository;
import com.mandi.repository.PriceRepository;
import com.mandi.repository.SeriesRef;
import com.mandi.web.dto.PriceViews;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Everything the price screens read: catalogue, series with statistics, board, comparison, seasonality. */
@Service
@Transactional(readOnly = true)
public class PriceQueryService {

    public static final int MAX_DAYS = 1095;
    /** Extra history fetched before a chart's first day so its moving average starts on day one. */
    static final int WARM_UP_DAYS = 14;
    /** A series that has not reported for this long is shown as stale on the board. */
    static final int STALE_AFTER_DAYS = 7;
    static final int SPARK_POINTS = 14;

    private final PriceRepository prices;
    private final CommodityRepository commodities;
    private final MarketRepository markets;
    private final ImportRunRepository runs;

    public PriceQueryService(PriceRepository prices, CommodityRepository commodities, MarketRepository markets,
                             ImportRunRepository runs) {
        this.prices = prices;
        this.commodities = commodities;
        this.markets = markets;
        this.runs = runs;
    }

    public PriceViews.Catalog catalog() {
        Map<Long, PriceViews.CommodityRef> byCommodity = new LinkedHashMap<>();
        TreeSet<String> categories = new TreeSet<>();
        for (SeriesRef s : prices.seriesIndex()) {
            categories.add(s.category());
            byCommodity.computeIfAbsent(s.commodityId(),
                            id -> new PriceViews.CommodityRef(id, s.commodity(), s.category(), new ArrayList<>()))
                    .markets().add(new PriceViews.MarketRef(s.marketId(), s.market(), s.district(), s.state(),
                            s.firstDate(), s.lastDate(), s.days()));
        }
        return new PriceViews.Catalog(prices.latestDate(), List.copyOf(categories), List.copyOf(byCommodity.values()));
    }

    public PriceViews.Series series(Long commodityId, Long marketId, int days) {
        if (days < 7 || days > MAX_DAYS) {
            throw ApiException.badRequest("days must be between 7 and " + MAX_DAYS);
        }
        Commodity commodity = commodity(commodityId);
        Market market = market(marketId);
        LocalDate latest = prices.latestDate(commodityId, marketId);
        if (latest == null) {
            throw ApiException.notFound("Prices for " + commodity.getName() + " in " + market.getName());
        }
        LocalDate from = latest.minusDays(days - 1L);
        // Statistics always look at the last 30 days, even when the chart shows a shorter window.
        LocalDate fetchFrom = (from.isBefore(latest.minusDays(29)) ? from : latest.minusDays(29)).minusDays(WARM_UP_DAYS);
        List<PricePoint> all = points(prices.series(commodityId, marketId, fetchFrom));
        List<Integer> ma7 = PriceAnalytics.movingAverage(all, 7);
        List<PriceViews.Point> shown = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            PricePoint p = all.get(i);
            if (!p.date().isBefore(from)) {
                shown.add(new PriceViews.Point(p.date(), p.min(), p.max(), p.modal(), ma7.get(i)));
            }
        }
        return new PriceViews.Series(commodity.getId(), commodity.getName(), commodity.getCategory(), market.getId(),
                market.getName(), market.getDistrict(), market.getState(), days, shown, PriceAnalytics.stats(all));
    }

    public PriceViews.Board board() {
        LocalDate latest = prices.latestDate();
        PriceViews.LastImport lastImport = runs.findFirstByStatusNotOrderByStartedAtDescIdDesc(ImportStatus.RUNNING)
                .map(r -> new PriceViews.LastImport(r.getSource(), r.getStatus().name(), r.getFinishedAt(), r.getLastDate()))
                .orElse(null);
        if (latest == null) {
            return new PriceViews.Board(null, List.of(), List.of(), List.of(), lastImport);
        }
        List<PriceViews.BoardRow> rows = boardRows(prices.board(latest.minusDays(20)), latest);
        List<PriceViews.BoardRow> fresh = rows.stream().filter(r -> !r.stale() && r.weekChangePct() != null).toList();
        List<PriceViews.BoardRow> gainers = fresh.stream().filter(r -> r.weekChangePct() > 0)
                .sorted(Comparator.comparing(PriceViews.BoardRow::weekChangePct).reversed()).limit(5).toList();
        List<PriceViews.BoardRow> losers = fresh.stream().filter(r -> r.weekChangePct() < 0)
                .sorted(Comparator.comparing(PriceViews.BoardRow::weekChangePct)).limit(5).toList();
        return new PriceViews.Board(latest, rows, gainers, losers, lastImport);
    }

    public PriceViews.Compare compare(Long commodityId) {
        Commodity commodity = commodity(commodityId);
        LocalDate latest = prices.latestDate();
        List<PriceViews.BoardRow> rows = latest == null ? List.of()
                : boardRows(prices.boardForCommodity(commodityId, latest.minusDays(20)), latest).stream()
                .filter(r -> !r.stale())
                .sorted(Comparator.comparingInt(PriceViews.BoardRow::modal).reversed()).toList();
        if (rows.isEmpty()) {
            return new PriceViews.Compare(commodityId, commodity.getName(), latest, rows, null, null, 0, null);
        }
        PriceViews.BoardRow high = rows.get(0);
        PriceViews.BoardRow low = rows.get(rows.size() - 1);
        return new PriceViews.Compare(commodityId, commodity.getName(), latest, rows, high.market(), low.market(),
                high.modal() - low.modal(), rows.size() > 1 ? PriceAnalytics.pctChange(low.modal(), high.modal()) : null);
    }

    /** Average modal price per calendar month over the last year: shows the seasonal pattern. */
    public PriceViews.Monthly monthly(Long commodityId, Long marketId) {
        Commodity commodity = commodity(commodityId);
        Market market = market(marketId);
        LocalDate latest = prices.latestDate(commodityId, marketId);
        List<PriceViews.Month> months = new ArrayList<>();
        if (latest != null) {
            Map<YearMonth, List<PricePoint>> grouped = new TreeMap<>();
            for (PricePoint p : points(prices.series(commodityId, marketId, latest.minusYears(1).plusDays(1)))) {
                grouped.computeIfAbsent(YearMonth.from(p.date()), k -> new ArrayList<>()).add(p);
            }
            grouped.forEach((ym, list) -> months.add(new PriceViews.Month(ym.getYear(), ym.getMonthValue(),
                    (int) Math.round(list.stream().mapToInt(PricePoint::modal).average().orElse(0)),
                    list.stream().mapToInt(PricePoint::modal).min().orElse(0),
                    list.stream().mapToInt(PricePoint::modal).max().orElse(0), list.size())));
        }
        return new PriceViews.Monthly(commodityId, commodity.getName(), marketId, market.getName(), months);
    }

    /** The latest board row for one series, or null if it has no prices (used by the watchlist). */
    public PriceViews.BoardRow latestRow(Long commodityId, Long marketId) {
        LocalDate latest = prices.latestDate(commodityId, marketId);
        if (latest == null) {
            return null;
        }
        LocalDate overall = prices.latestDate();
        Commodity c = commodity(commodityId);
        Market m = market(marketId);
        List<PricePoint> pts = points(prices.series(commodityId, marketId, latest.minusDays(20)));
        return row(c.getId(), c.getName(), c.getCategory(), m.getId(), m.getName(), m.getDistrict(), pts, overall);
    }

    /** The latest modal price of a series and its date, for alerts. */
    public PricePoint latestPoint(Long commodityId, Long marketId) {
        LocalDate latest = prices.latestDate(commodityId, marketId);
        if (latest == null) {
            return null;
        }
        List<PricePoint> pts = points(prices.series(commodityId, marketId, latest));
        return pts.isEmpty() ? null : pts.get(pts.size() - 1);
    }

    public String csv(Long commodityId, Long marketId, int days) {
        PriceViews.Series s = series(commodityId, marketId, days);
        StringBuilder out = new StringBuilder("date,commodity,market,district,state,min_price,max_price,modal_price,moving_avg_7\n");
        for (PriceViews.Point p : s.points()) {
            out.append(p.date()).append(',').append(csvCell(s.commodity())).append(',').append(csvCell(s.market()))
                    .append(',').append(csvCell(s.district())).append(',').append(csvCell(s.state())).append(',')
                    .append(p.min()).append(',').append(p.max()).append(',').append(p.modal()).append(',')
                    .append(p.ma7() == null ? "" : p.ma7()).append('\n');
        }
        return out.toString();
    }

    static String csvCell(String value) {
        String v = value;
        // Cells starting with these are run as formulas by Excel; prefix a quote (CSV injection).
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            v = "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    static List<PricePoint> points(List<DailyRow> rows) {
        List<PricePoint> out = new ArrayList<>(rows.size());
        for (DailyRow r : rows) {
            int modal = (int) Math.round(r.modal());
            // Averaging varieties can never leave the min–max range, but rounding could by a rupee: clamp.
            out.add(new PricePoint(r.date(), Math.min(r.min(), modal), Math.max(r.max(), modal), modal));
        }
        return out;
    }

    static List<PriceViews.BoardRow> boardRows(List<BoardRow> rows, LocalDate overallLatest) {
        Map<String, List<BoardRow>> bySeries = new LinkedHashMap<>();
        for (BoardRow r : rows) {
            bySeries.computeIfAbsent(r.commodityId() + ":" + r.marketId(), k -> new ArrayList<>()).add(r);
        }
        List<PriceViews.BoardRow> out = new ArrayList<>();
        for (List<BoardRow> series : bySeries.values()) {
            BoardRow first = series.get(0);
            List<PricePoint> pts = points(series.stream()
                    .map(r -> new DailyRow(r.date(), r.min(), r.max(), r.modal())).toList());
            out.add(row(first.commodityId(), first.commodity(), first.category(), first.marketId(), first.market(),
                    first.district(), pts, overallLatest));
        }
        return out;
    }

    static PriceViews.BoardRow row(Long commodityId, String commodity, String category, Long marketId, String market,
                                   String district, List<PricePoint> pts, LocalDate overallLatest) {
        SeriesStats s = PriceAnalytics.stats(pts);
        List<Integer> spark = pts.subList(Math.max(0, pts.size() - SPARK_POINTS), pts.size()).stream()
                .map(PricePoint::modal).toList();
        boolean stale = overallLatest != null && s.latestDate().isBefore(overallLatest.minusDays(STALE_AFTER_DAYS));
        return new PriceViews.BoardRow(commodityId, commodity, category, marketId, market, district, s.latestDate(),
                s.latestMin(), s.latestMax(), s.latestModal(), s.dayChange(), s.dayChangePct(), s.weekChange(),
                s.weekChangePct(), spark, stale);
    }

    public Commodity commodity(Long id) {
        return commodities.findById(id).orElseThrow(() -> ApiException.notFound("Commodity"));
    }

    public Market market(Long id) {
        return markets.findById(id).orElseThrow(() -> ApiException.notFound("Market"));
    }
}
