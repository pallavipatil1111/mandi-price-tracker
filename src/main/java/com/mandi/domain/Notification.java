package com.mandi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/** An in-app message created when an alert fires. Kept even if the alert is later deleted. */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "alert_id")
    private Long alertId;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(nullable = false)
    private int price;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(Long userId, Long alertId, String message, int price, LocalDate priceDate, Instant createdAt) {
        this.userId = userId;
        this.alertId = alertId;
        this.message = message;
        this.price = price;
        this.priceDate = priceDate;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAlertId() {
        return alertId;
    }

    public String getMessage() {
        return message;
    }

    public int getPrice() {
        return price;
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }
}
