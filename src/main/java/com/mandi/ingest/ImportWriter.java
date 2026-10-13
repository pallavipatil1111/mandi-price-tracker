package com.mandi.ingest;

import com.mandi.core.Categories;
import com.mandi.core.CleanRecord;
import com.mandi.domain.Commodity;
import com.mandi.domain.Market;
import com.mandi.domain.Price;
import com.mandi.repository.CommodityRepository;
import com.mandi.repository.MarketRepository;
import com.mandi.repository.PriceRepository;
import com.mandi.service.SeriesKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Upserts validated rows in one transaction. Existing rows for the batch's dates are loaded with a single
 * query and matched in memory on (market, commodity, variety, date), so an import of thousands of rows
 * does not run thousands of SELECTs, and re-importing a file never creates duplicates.
 */
@Component
public class ImportWriter {

    public record Result(int inserted, int updated, int unchanged, Set<SeriesKey> touched) {
    }

    private final PriceRepository prices;
    private final MarketRepository markets;
    private final CommodityRepository commodities;

    public ImportWriter(PriceRepository prices, MarketRepository markets, CommodityRepository commodities) {
        this.prices = prices;
        this.markets = markets;
        this.commodities = commodities;
    }

    @Transactional
    public Result write(List<CleanRecord> rows, String source, Instant now) {
        if (rows.isEmpty()) {
            return new Result(0, 0, 0, Set.of());
        }
        Map<String, Market> marketByKey = new HashMap<>();
        for (Market m : markets.findAll()) {
            marketByKey.put(m.key(), m);
        }
        Map<String, Commodity> commodityByName = new HashMap<>();
        for (Commodity c : commodities.findAll()) {
            commodityByName.put(c.getName().toLowerCase(Locale.ROOT), c);
        }
        Set<LocalDate> dates = new TreeSet<>();
        rows.forEach(r -> dates.add(r.date()));
        Map<String, Price> existing = new HashMap<>();
        for (Price p : prices.findForDates(dates)) {
            existing.put(p.key(), p);
        }

        int inserted = 0;
        int updated = 0;
        int unchanged = 0;
        Set<SeriesKey> touched = new LinkedHashSet<>();
        for (CleanRecord r : rows) {
            Market market = marketByKey.computeIfAbsent(r.state() + "|" + r.district() + "|" + r.market(),
                    k -> markets.save(new Market(r.state(), r.district(), r.market())));
            Commodity commodity = commodityByName.computeIfAbsent(r.commodity().toLowerCase(Locale.ROOT),
                    k -> commodities.save(new Commodity(r.commodity(), Categories.of(r.commodity()))));
            String key = String.join("|", market.getState(), market.getDistrict(), market.getName(),
                    commodity.getName(), r.variety(), r.date().toString());
            Price price = existing.get(key);
            if (price == null) {
                price = prices.save(new Price(market, commodity, r.variety(), r.date(), r.min(), r.max(), r.modal(),
                        source, now));
                existing.put(key, price);
                inserted++;
            } else if (price.revise(r.min(), r.max(), r.modal(), source, now)) {
                updated++;
            } else {
                unchanged++;
                continue;
            }
            touched.add(new SeriesKey(commodity.getId(), market.getId()));
        }
        return new Result(inserted, updated, unchanged, touched);
    }
}
