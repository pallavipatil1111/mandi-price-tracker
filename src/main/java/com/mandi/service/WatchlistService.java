package com.mandi.service;

import com.mandi.domain.WatchItem;
import com.mandi.repository.PriceRepository;
import com.mandi.repository.WatchItemRepository;
import com.mandi.web.dto.PriceViews;
import com.mandi.web.dto.UserViews;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WatchlistService {

    static final int MAX_ITEMS = 30;

    private final WatchItemRepository items;
    private final PriceRepository prices;
    private final PriceQueryService query;
    private final Clock clock;

    public WatchlistService(WatchItemRepository items, PriceRepository prices, PriceQueryService query, Clock clock) {
        this.items = items;
        this.prices = prices;
        this.query = query;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserViews.WatchCard> list(Long userId) {
        return items.findForUser(userId).stream()
                .map(w -> new UserViews.WatchCard(w.getId(), query.latestRow(w.getCommodity().getId(), w.getMarket().getId())))
                .toList();
    }

    @Transactional
    public UserViews.WatchCard add(Long userId, UserViews.WatchRequest request) {
        if (prices.latestDate(request.commodityId(), request.marketId()) == null) {
            throw ApiException.notFound("Prices for that commodity in that market");
        }
        if (items.findByUserIdAndCommodityIdAndMarketId(userId, request.commodityId(), request.marketId()).isPresent()) {
            throw ApiException.conflict("Already on your watchlist");
        }
        if (items.findForUser(userId).size() >= MAX_ITEMS) {
            throw ApiException.badRequest("A watchlist can hold up to " + MAX_ITEMS + " prices");
        }
        WatchItem item = items.save(new WatchItem(userId, query.commodity(request.commodityId()),
                query.market(request.marketId()), clock.instant()));
        PriceViews.BoardRow latest = query.latestRow(request.commodityId(), request.marketId());
        return new UserViews.WatchCard(item.getId(), latest);
    }

    @Transactional
    public void remove(Long userId, Long id) {
        // Scoped to the caller: someone else's item id is simply "not found".
        items.delete(items.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Watchlist item")));
    }
}
