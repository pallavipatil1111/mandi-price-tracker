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
import java.time.LocalDate;

/** A day's price for one commodity variety in one market, in whole ₹ per quintal. */
@Entity
@Table(name = "prices")
public class Price {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "market_id")
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commodity_id")
    private Commodity commodity;

    @Column(nullable = false, length = 80)
    private String variety;

    @Column(name = "arrival_date", nullable = false)
    private LocalDate arrivalDate;

    @Column(name = "min_price", nullable = false)
    private int minPrice;

    @Column(name = "max_price", nullable = false)
    private int maxPrice;

    @Column(name = "modal_price", nullable = false)
    private int modalPrice;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Price() {
    }

    public Price(Market market, Commodity commodity, String variety, LocalDate arrivalDate,
                 int minPrice, int maxPrice, int modalPrice, String source, Instant now) {
        this.market = market;
        this.commodity = commodity;
        this.variety = variety;
        this.arrivalDate = arrivalDate;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
        this.modalPrice = modalPrice;
        this.source = source;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** Applies a re-imported row. Returns false (and touches nothing) if the prices are unchanged. */
    public boolean revise(int min, int max, int modal, String source, Instant now) {
        if (min == minPrice && max == maxPrice && modal == modalPrice) {
            return false;
        }
        this.minPrice = min;
        this.maxPrice = max;
        this.modalPrice = modal;
        this.source = source;
        this.updatedAt = now;
        return true;
    }

    public String key() {
        return String.join("|", market.getState(), market.getDistrict(), market.getName(), commodity.getName(),
                variety, arrivalDate.toString());
    }

    public Long getId() {
        return id;
    }

    public Market getMarket() {
        return market;
    }

    public Commodity getCommodity() {
        return commodity;
    }

    public String getVariety() {
        return variety;
    }

    public LocalDate getArrivalDate() {
        return arrivalDate;
    }

    public int getMinPrice() {
        return minPrice;
    }

    public int getMaxPrice() {
        return maxPrice;
    }

    public int getModalPrice() {
        return modalPrice;
    }

    public String getSource() {
        return source;
    }
}
