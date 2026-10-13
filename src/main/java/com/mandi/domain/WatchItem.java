package com.mandi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** A series (commodity in a market) that a user pinned to their watchlist. */
@Entity
@Table(name = "watch_items")
public class WatchItem {

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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WatchItem() {
    }

    public WatchItem(Long userId, Commodity commodity, Market market, Instant createdAt) {
        this.userId = userId;
        this.commodity = commodity;
        this.market = market;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
