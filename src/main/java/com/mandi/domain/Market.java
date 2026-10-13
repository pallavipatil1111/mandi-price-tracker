package com.mandi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** An APMC market yard, e.g. Lasalgaon in Nashik district. */
@Entity
@Table(name = "markets")
public class Market {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String state;

    @Column(nullable = false, length = 80)
    private String district;

    @Column(nullable = false, length = 120)
    private String name;

    protected Market() {
    }

    public Market(String state, String district, String name) {
        this.state = state;
        this.district = district;
        this.name = name;
    }

    public String key() {
        return state + "|" + district + "|" + name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getState() {
        return state;
    }

    public String getDistrict() {
        return district;
    }

    public String getName() {
        return name;
    }
}
