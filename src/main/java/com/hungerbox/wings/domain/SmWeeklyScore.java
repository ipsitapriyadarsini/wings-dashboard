package com.hungerbox.wings.domain;

import com.hungerbox.wings.scoring.NoScoreReason;
import com.hungerbox.wings.scoring.RosterAgg;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Published weekly snapshot for one SM x Week (calculated across every cafe the SM was rostered at). */
@Entity
@Table(name = "sm_weekly_score",
        uniqueConstraints = @UniqueConstraint(name = "uq_sws", columnNames = {"sm_id", "week_start"}),
        indexes = @Index(name = "ix_sws_week", columnList = "week_start"))
public class SmWeeklyScore {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "sm_id", nullable = false)
    private Long smId;
    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;
    private Double score; // null = No Score
    @Enumerated(EnumType.STRING)
    private NoScoreReason noScoreReason;
    private long rostered, leave, punched, noShow, valid, ciOnTime, coOnTime, ciLoc, coLoc;
    private long fillFilled, fillTotal, qualCompliant, qualEligible;
    private Instant computedAt;

    protected SmWeeklyScore() {}
    public SmWeeklyScore(Long smId, LocalDate weekStart, RosterAgg a, Double score, NoScoreReason reason) {
        this.smId = smId; this.weekStart = weekStart; this.score = score; this.noScoreReason = reason;
        this.rostered = a.rostered; this.leave = a.leave; this.punched = a.punched; this.noShow = a.noShow;
        this.valid = a.valid; this.ciOnTime = a.ciOnTime; this.coOnTime = a.coOnTime; this.ciLoc = a.ciLoc;
        this.coLoc = a.coLoc; this.fillFilled = a.fillFilled; this.fillTotal = a.fillTotal;
        this.qualCompliant = a.qualCompliant; this.qualEligible = a.qualEligible;
        this.computedAt = Instant.now();
    }

    public RosterAgg toAgg() {
        RosterAgg a = new RosterAgg();
        a.rostered = rostered; a.leave = leave; a.punched = punched; a.noShow = noShow; a.valid = valid;
        a.ciOnTime = ciOnTime; a.coOnTime = coOnTime; a.ciLoc = ciLoc; a.coLoc = coLoc;
        a.fillFilled = fillFilled; a.fillTotal = fillTotal; a.qualCompliant = qualCompliant; a.qualEligible = qualEligible;
        return a;
    }

    public Long getSmId() { return smId; }
    public LocalDate getWeekStart() { return weekStart; }
    public Double getScore() { return score; }
    public NoScoreReason getNoScoreReason() { return noScoreReason; }
    public long getRostered() { return rostered; }
}
