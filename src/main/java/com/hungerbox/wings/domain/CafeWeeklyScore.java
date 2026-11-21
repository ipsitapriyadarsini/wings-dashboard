package com.hungerbox.wings.domain;

import com.hungerbox.wings.scoring.CafeAgg;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Published weekly snapshot for one Cafe x Week (the smallest calculation unit). Raw counters are stored so any
 * combination of cafes/weeks can be re-scored from summed data. Weeks run Monday-Sunday.
 */
@Entity
@Table(name = "cafe_weekly_score",
        uniqueConstraints = @UniqueConstraint(name = "uq_cws", columnNames = {"cafe_id", "week_start"}),
        indexes = @Index(name = "ix_cws_week", columnList = "week_start"))
public class CafeWeeklyScore {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "cafe_id", nullable = false)
    private Long cafeId;
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;
    private Double score; // null = No Score
    private long ratingSum, ratingCount, fillFilled, fillTotal, qualCompliant, qualEligible;
    private Instant computedAt;

    protected CafeWeeklyScore() {}
    public CafeWeeklyScore(Long cafeId, LocalDate weekStart, CafeAgg a, Double score) {
        this.cafeId = cafeId; this.weekStart = weekStart; this.score = score;
        this.ratingSum = a.ratingSum(); this.ratingCount = a.ratingCount();
        this.fillFilled = a.fillFilled(); this.fillTotal = a.fillTotal();
        this.qualCompliant = a.qualCompliant(); this.qualEligible = a.qualEligible();
        this.computedAt = Instant.now();
    }

    public CafeAgg toAgg() { return new CafeAgg(ratingSum, ratingCount, fillFilled, fillTotal, qualCompliant, qualEligible); }
    public Long getCafeId() { return cafeId; }
    public LocalDate getWeekStart() { return weekStart; }
    public Double getScore() { return score; }
}
