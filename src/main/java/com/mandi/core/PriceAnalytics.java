package com.mandi.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Pure calculations over a price series sorted by date (oldest first). No database, easy to test. */
public final class PriceAnalytics {

    /** A trend counts as rising or falling if the 14-day fitted line moves more than this share of the average. */
    static final double TREND_THRESHOLD = 0.03;

    private PriceAnalytics() {
    }

    /**
     * Trailing moving average of the modal price over the last {@code window} trading days. The first
     * {@code window - 1} points have no full window and get null, so the line starts where it is honest.
     */
    public static List<Integer> movingAverage(List<PricePoint> points, int window) {
        if (window < 1) {
            throw new IllegalArgumentException("window must be at least 1");
        }
        List<Integer> result = new ArrayList<>(points.size());
        long sum = 0;
        for (int i = 0; i < points.size(); i++) {
            sum += points.get(i).modal();
            if (i >= window) {
                sum -= points.get(i - window).modal();
            }
            result.add(i >= window - 1 ? (int) Math.round(sum / (double) window) : null);
        }
        return result;
    }

    /** Percentage change from {@code from} to {@code to}, rounded to one decimal place. */
    public static double pctChange(int from, int to) {
        if (from <= 0) {
            throw new IllegalArgumentException("base price must be positive");
        }
        return Math.round((to - from) * 1000.0 / from) / 10.0;
    }

    public static SeriesStats stats(List<PricePoint> points) {
        if (points.isEmpty()) {
            throw new IllegalArgumentException("a series needs at least one price");
        }
        PricePoint latest = points.get(points.size() - 1);

        Integer dayChange = null;
        Double dayPct = null;
        if (points.size() > 1) {
            PricePoint prev = points.get(points.size() - 2);
            dayChange = latest.modal() - prev.modal();
            dayPct = pctChange(prev.modal(), latest.modal());
        }

        Integer weekChange = null;
        Double weekPct = null;
        PricePoint weekAgo = lastOnOrBefore(points, latest.date().minusDays(7));
        if (weekAgo != null) {
            weekChange = latest.modal() - weekAgo.modal();
            weekPct = pctChange(weekAgo.modal(), latest.modal());
        }

        List<PricePoint> month = since(points, latest.date().minusDays(29));
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        long sum = 0;
        for (PricePoint p : month) {
            low = Math.min(low, p.modal());
            high = Math.max(high, p.modal());
            sum += p.modal();
        }
        int avg = (int) Math.round(sum / (double) month.size());

        double slope = slopePerDay(since(points, latest.date().minusDays(13)));
        Trend trend = Trend.STEADY;
        if (Math.abs(slope * 14) > TREND_THRESHOLD * avg) {
            trend = slope > 0 ? Trend.RISING : Trend.FALLING;
        }
        return new SeriesStats(latest.date(), latest.modal(), latest.min(), latest.max(), dayChange, dayPct,
                weekChange, weekPct, low, high, avg, round1(volatility(month)), round1(slope), trend, month.size());
    }

    /** Standard deviation of the day-to-day % changes (population), 0 for fewer than two changes. */
    static double volatility(List<PricePoint> points) {
        if (points.size() < 3) {
            return 0;
        }
        double[] changes = new double[points.size() - 1];
        double mean = 0;
        for (int i = 1; i < points.size(); i++) {
            changes[i - 1] = (points.get(i).modal() - points.get(i - 1).modal()) * 100.0 / points.get(i - 1).modal();
            mean += changes[i - 1];
        }
        mean /= changes.length;
        double variance = 0;
        for (double c : changes) {
            variance += (c - mean) * (c - mean);
        }
        return Math.sqrt(variance / changes.length);
    }

    /** Least-squares slope of modal price against calendar day (gaps for closed days are respected). */
    static double slopePerDay(List<PricePoint> points) {
        if (points.size() < 2) {
            return 0;
        }
        LocalDate origin = points.get(0).date();
        double n = points.size();
        double sx = 0;
        double sy = 0;
        double sxx = 0;
        double sxy = 0;
        for (PricePoint p : points) {
            double x = p.date().toEpochDay() - origin.toEpochDay();
            sx += x;
            sy += p.modal();
            sxx += x * x;
            sxy += x * p.modal();
        }
        double denominator = n * sxx - sx * sx;
        return denominator == 0 ? 0 : (n * sxy - sx * sy) / denominator;
    }

    static PricePoint lastOnOrBefore(List<PricePoint> points, LocalDate date) {
        for (int i = points.size() - 1; i >= 0; i--) {
            if (!points.get(i).date().isAfter(date)) {
                return points.get(i);
            }
        }
        return null;
    }

    static List<PricePoint> since(List<PricePoint> points, LocalDate from) {
        int i = points.size();
        while (i > 0 && !points.get(i - 1).date().isBefore(from)) {
            i--;
        }
        return points.subList(i, points.size());
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
