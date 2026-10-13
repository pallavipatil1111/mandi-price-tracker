package com.mandi.domain;

import com.mandi.core.AlertDirection;
import com.mandi.core.AlertRule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** "Tell me when onion at Lasalgaon goes above ₹2,500 a quintal." */
@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commodity_id")
    private Commodity commodity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_id")
    private Market market;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private AlertDirection direction;

    @Column(nullable = false)
    private int threshold;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean armed = true;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Alert() {
    }

    public Alert(Long userId, Commodity commodity, Market market, AlertDirection direction, int threshold,
                 Instant createdAt) {
        this.userId = userId;
        this.commodity = commodity;
        this.market = market;
        this.direction = direction;
        this.threshold = threshold;
        this.createdAt = createdAt;
    }

    /** Applies the edge-trigger rule (with hysteresis) to a new price; true if the user should be notified. */
    public boolean observe(int price, Instant now) {
        AlertRule.Decision decision = AlertRule.decide(armed, direction.isMet(price, threshold),
                direction.hasReset(price, threshold));
        switch (decision) {
            case FIRE -> {
                armed = false;
                lastTriggeredAt = now;
                return true;
            }
            case REARM -> armed = true;
            case NONE -> {
                // unchanged
            }
        }
        return false;
    }

    /**
     * A new or edited alert must not fire for a price that already satisfies it (the user can see that
     * price on screen); it starts disarmed and fires on the next fresh crossing.
     */
    public void armFor(Integer currentPrice) {
        this.armed = currentPrice == null || !direction.isMet(currentPrice, threshold);
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public Commodity getCommodity() {
        return commodity;
    }

    public Market getMarket() {
        return market;
    }

    public AlertDirection getDirection() {
        return direction;
    }

    public int getThreshold() {
        return threshold;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isArmed() {
        return armed;
    }

    public Instant getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
