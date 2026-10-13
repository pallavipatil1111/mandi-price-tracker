package com.mandi.web.dto;

import com.mandi.core.AlertDirection;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Requests and responses for the signed-in features: watchlist, alerts and notifications. */
public final class UserViews {

    private UserViews() {
    }

    public record WatchRequest(@NotNull Long commodityId, @NotNull Long marketId) {
    }

    public record WatchCard(Long id, PriceViews.BoardRow latest) {
    }

    public record AlertRequest(@NotNull Long commodityId, @NotNull Long marketId, @NotNull AlertDirection direction,
                               @NotNull @Min(1) @Max(2_000_000) Integer threshold) {
    }

    public record AlertToggle(@NotNull Boolean active) {
    }

    public record AlertView(Long id, Long commodityId, String commodity, Long marketId, String market,
                            AlertDirection direction, int threshold, boolean active, boolean armed,
                            Integer currentPrice, LocalDate currentDate, boolean conditionMet,
                            Instant lastTriggeredAt, Instant createdAt) {
    }

    public record NotificationView(Long id, Long alertId, String message, int price, LocalDate priceDate,
                                   Instant createdAt, boolean read) {
    }

    public record Inbox(long unread, List<NotificationView> items) {
    }
}
