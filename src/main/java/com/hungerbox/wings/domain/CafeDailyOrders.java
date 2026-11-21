package com.hungerbox.wings.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

/** Eligible orders per cafe per day: the denominator of Rating Capture Rate %. */
@Entity
@Table(name = "cafe_daily_orders", uniqueConstraints = @UniqueConstraint(columnNames = {"cafe_id", "order_date"}))
public class CafeDailyOrders {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cafe_id")
    private Cafe cafe;
    @Column(name = "order_date", nullable = false)
    private LocalDate orderDate;
    private int eligibleOrders;

    protected CafeDailyOrders() {}
    public CafeDailyOrders(Cafe cafe, LocalDate orderDate, int eligibleOrders) {
        this.cafe = cafe; this.orderDate = orderDate; this.eligibleOrders = eligibleOrders;
    }
}
