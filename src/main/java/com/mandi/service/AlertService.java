package com.mandi.service;

import com.mandi.core.AlertDirection;
import com.mandi.core.Money;
import com.mandi.core.PricePoint;
import com.mandi.domain.Alert;
import com.mandi.domain.Notification;
import com.mandi.repository.AlertRepository;
import com.mandi.repository.NotificationRepository;
import com.mandi.web.dto.UserViews;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Price alerts and the in-app notifications they produce. */
@Service
public class AlertService {

    static final int MAX_ALERTS = 20;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final AlertRepository alerts;
    private final NotificationRepository notifications;
    private final PriceQueryService query;
    private final Clock clock;

    public AlertService(AlertRepository alerts, NotificationRepository notifications, PriceQueryService query,
                        Clock clock) {
        this.alerts = alerts;
        this.notifications = notifications;
        this.query = query;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserViews.AlertView> list(Long userId) {
        return alerts.findForUser(userId).stream().map(this::view).toList();
    }

    @Transactional
    public UserViews.AlertView create(Long userId, UserViews.AlertRequest request) {
        PricePoint current = query.latestPoint(request.commodityId(), request.marketId());
        if (current == null) {
            throw ApiException.notFound("Prices for that commodity in that market");
        }
        if (alerts.countByUserId(userId) >= MAX_ALERTS) {
            throw ApiException.badRequest("You can have up to " + MAX_ALERTS + " alerts - delete one first");
        }
        Alert alert = new Alert(userId, query.commodity(request.commodityId()), query.market(request.marketId()),
                request.direction(), request.threshold(), clock.instant());
        alert.armFor(current.modal());
        return view(alerts.save(alert), current);
    }

    @Transactional
    public UserViews.AlertView setActive(Long userId, Long id, boolean active) {
        Alert alert = owned(userId, id);
        if (active && !alert.isActive()) {
            // Resuming: judge against today's price, not whatever happened while paused.
            PricePoint current = query.latestPoint(alert.getCommodity().getId(), alert.getMarket().getId());
            alert.armFor(current == null ? null : current.modal());
        }
        alert.setActive(active);
        return view(alert);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        alerts.delete(owned(userId, id));
    }

    /**
     * Checks the active alerts on every series that just received new prices. Returns how many fired.
     * Runs in the import's flow after the prices are committed.
     */
    @Transactional
    public int evaluate(Collection<SeriesKey> touched) {
        int fired = 0;
        for (SeriesKey key : touched) {
            List<Alert> watching = alerts.findActiveForSeries(key.commodityId(), key.marketId());
            if (watching.isEmpty()) {
                continue;
            }
            PricePoint latest = query.latestPoint(key.commodityId(), key.marketId());
            if (latest == null) {
                continue;
            }
            for (Alert alert : watching) {
                if (alert.observe(latest.modal(), clock.instant())) {
                    notifications.save(new Notification(alert.getUserId(), alert.getId(), message(alert, latest),
                            latest.modal(), latest.date(), clock.instant()));
                    fired++;
                }
            }
        }
        return fired;
    }

    @Transactional(readOnly = true)
    public UserViews.Inbox inbox(Long userId) {
        List<UserViews.NotificationView> items = notifications.findTop50ByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                .map(n -> new UserViews.NotificationView(n.getId(), n.getAlertId(), n.getMessage(), n.getPrice(),
                        n.getPriceDate(), n.getCreatedAt(), n.getReadAt() != null))
                .toList();
        return new UserViews.Inbox(notifications.countByUserIdAndReadAtIsNull(userId), items);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notifications.markAllRead(userId, clock.instant());
    }

    static String message(Alert alert, PricePoint price) {
        String verb = alert.getDirection() == AlertDirection.ABOVE ? "rose to " : "fell to ";
        return alert.getCommodity().getName() + " at " + alert.getMarket().getName() + " " + verb
                + Money.rupees(price.modal()) + "/quintal on " + price.date().format(DAY) + " (your alert: "
                + alert.getDirection().name().toLowerCase(Locale.ROOT) + " " + Money.rupees(alert.getThreshold()) + ")";
    }

    private Alert owned(Long userId, Long id) {
        return alerts.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Alert"));
    }

    private UserViews.AlertView view(Alert a) {
        return view(a, query.latestPoint(a.getCommodity().getId(), a.getMarket().getId()));
    }

    private UserViews.AlertView view(Alert a, PricePoint current) {
        Integer price = current == null ? null : current.modal();
        return new UserViews.AlertView(a.getId(), a.getCommodity().getId(), a.getCommodity().getName(),
                a.getMarket().getId(), a.getMarket().getName(), a.getDirection(), a.getThreshold(), a.isActive(),
                a.isArmed(), price, current == null ? null : current.date(),
                price != null && a.getDirection().isMet(price, a.getThreshold()), a.getLastTriggeredAt(), a.getCreatedAt());
    }
}
