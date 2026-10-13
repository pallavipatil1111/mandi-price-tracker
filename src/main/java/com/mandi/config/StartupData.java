package com.mandi.config;

import com.mandi.core.AlertDirection;
import com.mandi.core.RecordValidator;
import com.mandi.domain.ImportTrigger;
import com.mandi.domain.Role;
import com.mandi.domain.User;
import com.mandi.ingest.ImportService;
import com.mandi.ingest.PriceSource;
import com.mandi.repository.CommodityRepository;
import com.mandi.repository.MarketRepository;
import com.mandi.repository.PriceRepository;
import com.mandi.repository.UserRepository;
import com.mandi.service.AlertService;
import com.mandi.service.PriceQueryService;
import com.mandi.service.WatchlistService;
import com.mandi.web.dto.UserViews;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * First start: creates the admin account (ADMIN_EMAIL / ADMIN_PASSWORD) and, with DEMO_DATA=true,
 * fills the database with a year of history through the real import pipeline, plus a demo user whose
 * watchlist and alerts were created a month ago, so the last 30 days of imports fire real notifications.
 */
@Configuration
public class StartupData {

    public static final String DEMO_EMAIL = "demo@mandi.in";
    public static final String DEMO_PASSWORD = "demo1234";
    static final int HISTORY_DAYS = 365;
    static final int ALERT_DAYS = 30;

    private static final Logger log = LoggerFactory.getLogger(StartupData.class);

    /** commodity, market, and whether the demo alert should be set out of reach (still waiting). */
    private record DemoSeries(String commodity, String market, boolean waiting) {
    }

    private static final List<DemoSeries> DEMO = List.of(
            new DemoSeries("Onion", "Lasalgaon", false), new DemoSeries("Tomato", "Pune", false),
            new DemoSeries("Turmeric", "Sangli", false), new DemoSeries("Soyabean", "Latur", false),
            new DemoSeries("Cotton", "Akola", true));

    @Bean
    public CommandLineRunner seed(UserRepository users, PasswordEncoder encoder, PriceRepository prices,
                                  CommodityRepository commodities, MarketRepository markets, PriceSource source,
                                  ImportService imports, PriceQueryService query, WatchlistService watchlist,
                                  AlertService alerts, Clock clock,
                                  @Value("${app.admin.email}") String adminEmail,
                                  @Value("${app.admin.password}") String adminPassword,
                                  @Value("${app.demo-data:false}") boolean demoData) {
        return args -> {
            if (!users.existsByEmailIgnoreCase(adminEmail)) {
                users.save(new User("Market admin", adminEmail.toLowerCase(), encoder.encode(adminPassword), Role.ADMIN));
                log.info("Created admin account {}", adminEmail);
            }
            if (!demoData || prices.count() > 0) {
                return;
            }
            if (!source.supportsHistory()) {
                log.info("Empty database: importing today's prices from {}", source.name());
                imports.importToday(ImportTrigger.BACKFILL);
                return;
            }
            LocalDate today = LocalDate.now(clock);
            imports.run(ImportTrigger.BACKFILL, days(today.minusDays(HISTORY_DAYS - 1L), today.minusDays(ALERT_DAYS + 1L)), false);

            User demo = users.findByEmailIgnoreCase(DEMO_EMAIL).orElseGet(() ->
                    users.save(new User("Demo Farmer", DEMO_EMAIL, encoder.encode(DEMO_PASSWORD), Role.USER)));
            List<LocalDate> recent = days(today.minusDays(ALERT_DAYS), today);
            for (DemoSeries d : DEMO) {
                var commodity = commodities.findByNameIgnoreCase(d.commodity()).orElseThrow();
                var market = markets.findFirstByNameIgnoreCase(d.market()).orElseThrow();
                watchlist.add(demo.getId(), new UserViews.WatchRequest(commodity.getId(), market.getId()));
                int start = query.latestPoint(commodity.getId(), market.getId()).modal();
                List<Integer> ahead = upcoming(source, recent, today, d.commodity(), d.market());
                alerts.create(demo.getId(), demoAlert(commodity.getId(), market.getId(), start, ahead, d.waiting()));
            }
            imports.run(ImportTrigger.BACKFILL, recent, true);
            log.info("Demo data ready: {} prices; demo user {} / {}", prices.count(), DEMO_EMAIL, DEMO_PASSWORD);
        };
    }

    /**
     * Picks a threshold the coming month's prices will cross: 60% of the way to that month's extreme, in
     * whichever direction moved more, kept strictly beyond today's price and within the extreme. A
     * "waiting" alert is set 12% above the month's high, out of reach.
     */
    static UserViews.AlertRequest demoAlert(Long commodityId, Long marketId, int start, List<Integer> ahead,
                                            boolean waiting) {
        int high = ahead.stream().mapToInt(Integer::intValue).max().orElse(start);
        int low = ahead.stream().mapToInt(Integer::intValue).min().orElse(start);
        if (waiting || (high <= start && low >= start)) {
            return new UserViews.AlertRequest(commodityId, marketId, AlertDirection.ABOVE, round50(Math.max(high, start) * 1.12));
        }
        if (high - start >= start - low) {
            int target = round10(start + (high - start) * 0.6);
            return new UserViews.AlertRequest(commodityId, marketId, AlertDirection.ABOVE,
                    Math.min(high, Math.max(start + 1, target)));
        }
        int target = round10(start - (start - low) * 0.6);
        return new UserViews.AlertRequest(commodityId, marketId, AlertDirection.BELOW,
                Math.max(low, Math.min(start - 1, target)));
    }

    private static List<Integer> upcoming(PriceSource source, List<LocalDate> dates, LocalDate today,
                                          String commodity, String market) throws Exception {
        List<Integer> out = new ArrayList<>();
        for (LocalDate date : dates) {
            for (var raw : source.fetch(date)) {
                if (RecordValidator.validate(raw, today) instanceof RecordValidator.Ok ok
                        && ok.record().commodity().equals(commodity) && ok.record().market().equals(market)) {
                    out.add(ok.record().modal());
                }
            }
        }
        return out;
    }

    static List<LocalDate> days(LocalDate from, LocalDate to) {
        List<LocalDate> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            out.add(d);
        }
        return out;
    }

    static int round10(double value) {
        return (int) Math.max(10, Math.round(value / 10) * 10);
    }

    static int round50(double value) {
        return (int) Math.max(50, Math.round(value / 50) * 50);
    }
}
