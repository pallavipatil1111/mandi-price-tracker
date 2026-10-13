package com.mandi.web.dto;

import com.mandi.core.SeriesStats;
import java.time.LocalDate;
import java.util.List;

/** Read models for the price screens. Prices are whole ₹ per quintal. */
public final class PriceViews {

    private PriceViews() {
    }

    public record MarketRef(Long id, String name, String district, String state, LocalDate firstDate,
                            LocalDate lastDate, long tradingDays) {
    }

    public record CommodityRef(Long id, String name, String category, List<MarketRef> markets) {
    }

    public record Catalog(LocalDate latestDate, List<String> categories, List<CommodityRef> commodities) {
    }

    /** One day on the chart; ma7 is null until there are seven trading days to average. */
    public record Point(LocalDate date, int min, int max, int modal, Integer ma7) {
    }

    public record Series(Long commodityId, String commodity, String category, Long marketId, String market,
                         String district, String state, int days, List<Point> points, SeriesStats stats) {
    }

    /** A row of the market board: the latest price of one series, its changes and a 14-day sparkline. */
    public record BoardRow(Long commodityId, String commodity, String category, Long marketId, String market,
                           String district, LocalDate date, int min, int max, int modal,
                           Integer dayChange, Double dayChangePct, Integer weekChange, Double weekChangePct,
                           List<Integer> spark, boolean stale) {
    }

    public record LastImport(String source, String status, java.time.Instant finishedAt, LocalDate lastDate) {
    }

    public record Board(LocalDate latestDate, List<BoardRow> rows, List<BoardRow> gainers, List<BoardRow> losers,
                        LastImport lastImport) {
    }

    /** Where to sell (or buy): the latest price of one commodity in every market. */
    public record Compare(Long commodityId, String commodity, LocalDate latestDate, List<BoardRow> markets,
                          String highestMarket, String lowestMarket, int spread, Double spreadPct) {
    }

    public record Month(int year, int month, int avgModal, int low, int high, int tradingDays) {
    }

    public record Monthly(Long commodityId, String commodity, Long marketId, String market, List<Month> months) {
    }
}
