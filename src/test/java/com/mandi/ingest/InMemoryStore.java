package com.mandi.ingest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mandi.domain.Commodity;
import com.mandi.domain.ImportRun;
import com.mandi.domain.Market;
import com.mandi.domain.Price;
import com.mandi.repository.CommodityRepository;
import com.mandi.repository.ImportRunRepository;
import com.mandi.repository.MarketRepository;
import com.mandi.repository.PriceRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Mocked repositories backed by lists, so the import pipeline can be tested without a database. */
final class InMemoryStore {

    final PriceRepository prices = mock(PriceRepository.class);
    final MarketRepository markets = mock(MarketRepository.class);
    final CommodityRepository commodities = mock(CommodityRepository.class);
    final ImportRunRepository runs = mock(ImportRunRepository.class);

    final List<Price> priceRows = new ArrayList<>();
    final List<Market> marketRows = new ArrayList<>();
    final List<Commodity> commodityRows = new ArrayList<>();
    final List<ImportRun> runRows = new ArrayList<>();
    int findForDatesCalls;

    InMemoryStore() {
        when(markets.findAll()).thenAnswer(inv -> List.copyOf(marketRows));
        when(markets.save(any())).thenAnswer(inv -> {
            Market m = inv.getArgument(0);
            m.setId((long) marketRows.size() + 1);
            marketRows.add(m);
            return m;
        });
        when(commodities.findAll()).thenAnswer(inv -> List.copyOf(commodityRows));
        when(commodities.save(any())).thenAnswer(inv -> {
            Commodity c = inv.getArgument(0);
            c.setId((long) commodityRows.size() + 1);
            commodityRows.add(c);
            return c;
        });
        when(prices.findForDates(any())).thenAnswer(inv -> {
            findForDatesCalls++;
            Collection<LocalDate> dates = inv.getArgument(0);
            return priceRows.stream().filter(p -> dates.contains(p.getArrivalDate())).toList();
        });
        when(prices.save(any())).thenAnswer(inv -> {
            Price p = inv.getArgument(0);
            priceRows.add(p);
            return p;
        });
        when(runs.save(any())).thenAnswer(inv -> {
            ImportRun r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId((long) runRows.size() + 1);
                runRows.add(r);
            }
            return r;
        });
    }
}
