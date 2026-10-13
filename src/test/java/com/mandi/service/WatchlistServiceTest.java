package com.mandi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mandi.domain.WatchItem;
import com.mandi.repository.WatchItemRepository;
import com.mandi.web.dto.UserViews;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WatchlistServiceTest {

    private Fixture f;
    private final WatchItemRepository items = mock(WatchItemRepository.class);
    private final List<WatchItem> rows = new ArrayList<>();
    private WatchlistService service;

    @BeforeEach
    void setUp() {
        f = new Fixture();
        when(items.save(any())).thenAnswer(inv -> {
            rows.add(inv.getArgument(0));
            return inv.getArgument(0);
        });
        when(items.findForUser(any())).thenAnswer(inv -> List.copyOf(rows));
        when(items.findByUserIdAndCommodityIdAndMarketId(any(), any(), any())).thenAnswer(inv -> rows.stream()
                .filter(w -> w.getCommodity().getId().equals(inv.getArgument(1)) && w.getMarket().getId().equals(inv.getArgument(2)))
                .findFirst());
        when(items.findByIdAndUserId(any(), any())).thenReturn(Optional.empty());
        service = new WatchlistService(items, f.prices, f.query(), Fixture.CLOCK);
    }

    @Test
    void watchingASeriesReturnsItsLatestPriceAndChanges() {
        f.prices(f.onion, f.lasalgaon, Fixture.TODAY, 2000, 2100, 2050, 2000, 2100, 2200, 2300, 2400, 2500);

        UserViews.WatchCard card = service.add(7L, new UserViews.WatchRequest(f.onion.getId(), f.lasalgaon.getId()));

        assertThat(card.latest().modal()).isEqualTo(2500);
        assertThat(card.latest().dayChange()).isEqualTo(100);
        assertThat(card.latest().weekChange()).isEqualTo(400);
        assertThat(card.latest().spark()).hasSize(9);
        assertThat(service.list(7L)).hasSize(1);
    }

    @Test
    void duplicatesAndUnknownSeriesAreRefused() {
        f.prices(f.onion, f.lasalgaon, Fixture.TODAY, 2000);
        service.add(7L, new UserViews.WatchRequest(f.onion.getId(), f.lasalgaon.getId()));

        assertThatThrownBy(() -> service.add(7L, new UserViews.WatchRequest(f.onion.getId(), f.lasalgaon.getId())))
                .hasMessage("Already on your watchlist");
        assertThatThrownBy(() -> service.add(7L, new UserViews.WatchRequest(f.tomato.getId(), f.lasalgaon.getId())))
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> service.remove(8L, 1L)).hasMessage("Watchlist item not found");
    }
}
