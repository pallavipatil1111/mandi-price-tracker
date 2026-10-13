package com.mandi.ingest;

import com.mandi.core.RawRecord;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Offline stand-in for Agmarknet: 27 commodity-market series from real Maharashtra APMCs with
 * realistic price levels, seasonal peaks (onion in Oct–Nov, tomato in monsoon), shocks shared by all
 * markets of a commodity, Sunday closures, missing days, and the occasional malformed row.
 *
 * <p>Fully deterministic: the same date always produces the same rows, so re-importing a day is a
 * genuine "nothing changed" import, exactly like re-reading an unchanged government file.
 */
public class SampleSource implements PriceSource {

    static final LocalDate EPOCH = LocalDate.of(2024, 1, 1);
    private static final DateTimeFormatter AGMARKNET_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    /** One commodity: level in ₹/quintal, seasonal swing, day of year of the seasonal peak, daily shock size. */
    record Crop(String name, String variety, int base, double season, int peakDay, double shock, int roundTo) {
    }

    /** One market selling a crop, priced relative to the crop's state-wide level. */
    record Series(Crop crop, String market, String district, double factor, int index) {
    }

    private static final String STATE = "Maharashtra";
    static final List<Series> SERIES = new ArrayList<>();

    static {
        Crop onion = new Crop("Onion", "Red", 2100, 0.35, 319, 0.032, 10);
        Crop tomato = new Crop("Tomato", "Hybrid", 1700, 0.45, 191, 0.045, 10);
        Crop potato = new Crop("Potato", "Local", 1500, 0.15, 250, 0.014, 10);
        Crop soyabean = new Crop("Soyabean", "Yellow", 4550, 0.06, 190, 0.008, 5);
        Crop turmeric = new Crop("Turmeric", "Rajapuri", 13500, 0.10, 160, 0.012, 50);
        Crop cotton = new Crop("Cotton", "Medium Staple", 7250, 0.05, 15, 0.007, 5);
        Crop wheat = new Crop("Wheat", "Lokwan", 2650, 0.08, 20, 0.006, 5);
        Crop pomegranate = new Crop("Pomegranate", "Bhagwa", 8500, 0.25, 170, 0.028, 50);
        Crop chilli = new Crop("Green Chilli", "Other", 3600, 0.30, 175, 0.038, 10);
        Crop jaggery = new Crop("Gur(Jaggery)", "Yellow", 3900, 0.07, 220, 0.008, 10);
        add(onion, "Lasalgaon", "Nashik", 1.00);
        add(onion, "Pimpalgaon Baswant", "Nashik", 1.02);
        add(onion, "Pune", "Pune", 1.08);
        add(onion, "Solapur", "Solapur", 0.95);
        add(onion, "Ahmednagar", "Ahmednagar", 0.97);
        add(tomato, "Pune", "Pune", 1.05);
        add(tomato, "Narayangaon", "Pune", 0.98);
        add(tomato, "Nashik", "Nashik", 1.00);
        add(tomato, "Kolhapur", "Kolhapur", 1.06);
        add(potato, "Pune", "Pune", 1.00);
        add(potato, "Nagpur", "Nagpur", 0.96);
        add(potato, "Mumbai", "Thane", 1.10);
        add(soyabean, "Latur", "Latur", 1.00);
        add(soyabean, "Akola", "Akola", 0.99);
        add(soyabean, "Nagpur", "Nagpur", 0.98);
        add(turmeric, "Sangli", "Sangli", 1.00);
        add(turmeric, "Hingoli", "Hingoli", 0.94);
        add(cotton, "Akola", "Akola", 1.00);
        add(cotton, "Yavatmal", "Yavatmal", 0.99);
        add(wheat, "Pune", "Pune", 1.12);
        add(wheat, "Nagpur", "Nagpur", 1.00);
        add(pomegranate, "Solapur", "Solapur", 1.00);
        add(pomegranate, "Pune", "Pune", 1.10);
        add(chilli, "Pune", "Pune", 1.00);
        add(chilli, "Kolhapur", "Kolhapur", 0.95);
        add(jaggery, "Kolhapur", "Kolhapur", 1.00);
        add(jaggery, "Sangli", "Sangli", 0.97);
    }

    private static void add(Crop crop, String market, String district, double factor) {
        SERIES.add(new Series(crop, market, district, factor, SERIES.size()));
    }

    /** Cached random walks: per crop (shared shocks) and per series (local noise), indexed by days since EPOCH. */
    private final Map<String, double[]> cropWalks = new HashMap<>();
    private final Map<Integer, double[]> localWalks = new HashMap<>();

    @Override
    public String name() {
        return "sample";
    }

    @Override
    public boolean supportsHistory() {
        return true;
    }

    @Override
    public synchronized List<RawRecord> fetch(LocalDate date) {
        List<RawRecord> rows = new ArrayList<>();
        if (date.isBefore(EPOCH) || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return rows; // APMCs are closed on Sundays
        }
        int day = (int) (date.toEpochDay() - EPOCH.toEpochDay());
        String when = date.format(AGMARKNET_DATE);
        for (Series s : SERIES) {
            Random r = new Random(date.toEpochDay() * 1_000_003L + s.index() * 7919L);
            if (r.nextDouble() < 0.04) {
                continue; // market did not report that day
            }
            int modal = modal(s, date, day);
            int min = round(modal * (1 - 0.07 - r.nextDouble() * 0.13), s.crop().roundTo());
            int max = round(modal * (1 + 0.04 + r.nextDouble() * 0.10), s.crop().roundTo());
            String modalText = String.valueOf(modal);
            String market = s.market();
            double quirk = r.nextDouble();
            if (quirk < 0.004) {
                modalText = String.valueOf(max + 500); // a typo upstream: modal above max, gets rejected
            } else if (quirk < 0.007) {
                modalText = ""; // missing modal price, gets rejected
            } else if (quirk < 0.05) {
                modalText = String.format(java.util.Locale.ENGLISH, "%,d.00", modal); // "2,150.00" style
                market = "  " + market + " "; // stray whitespace, gets normalised
            }
            rows.add(new RawRecord(STATE, s.district(), market, s.crop().name(), s.crop().variety(), when,
                    String.valueOf(min), String.valueOf(max), modalText));
        }
        return rows;
    }

    int modal(Series s, LocalDate date, int day) {
        Crop crop = s.crop();
        double season = 1 + crop.season() * Math.cos(2 * Math.PI * (date.getDayOfYear() - crop.peakDay()) / 365.0);
        double shock = walk(cropWalks, crop.name().hashCode(), crop.name(), day, crop.shock(), 0.985)
                + walk(localWalks, s.index(), s.index(), day, crop.shock() * 0.35, 0.9);
        return round(crop.base() * s.factor() * season * Math.exp(shock), crop.roundTo());
    }

    /** Mean-reverting random walk (AR(1)), extended lazily and cached. */
    private static <K> double walk(Map<K, double[]> cache, long seed, K key, int day, double sigma, double keep) {
        double[] values = cache.get(key);
        if (values == null || values.length <= day) {
            int size = Math.max(day + 1, values == null ? 1024 : values.length * 2);
            double[] grown = new double[size];
            Random r = new Random(seed * 31 + 17);
            for (int i = 1; i < size; i++) {
                grown[i] = keep * grown[i - 1] + sigma * r.nextGaussian();
            }
            cache.put(key, grown);
            values = grown;
        }
        return values[day];
    }

    private static int round(double value, int step) {
        return (int) Math.max(step, Math.round(value / step) * step);
    }
}
