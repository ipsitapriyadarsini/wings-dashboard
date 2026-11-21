package com.hungerbox.wings.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * One rostered SM shift plus its attendance outcome. The four punctuality flags are evaluated at ingestion
 * time (30 min grace + 1 km radius); a "valid attendance" is one where all four are true (6/6, no LOPs).
 */
@Entity
@Table(name = "roster", indexes = {
        @Index(name = "ix_roster_cafe_date", columnList = "cafe_id,roster_date"),
        @Index(name = "ix_roster_sm_date", columnList = "sm_id,roster_date")})
public class Roster {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "sm_id")
    private AppUser sm;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cafe_id")
    private Cafe cafe;
    @Column(name = "roster_date", nullable = false)
    private LocalDate rosterDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private RosterStatus status = RosterStatus.ASSIGNED;
    private Boolean punched;
    private Boolean checkInOnTime;
    private Boolean checkOutOnTime;
    private Boolean checkInInLocation;
    private Boolean checkOutInLocation;

    protected Roster() {}
    public Roster(AppUser sm, Cafe cafe, LocalDate rosterDate, RosterStatus status, Boolean punched,
                  Boolean checkInOnTime, Boolean checkOutOnTime, Boolean checkInInLocation, Boolean checkOutInLocation) {
        this.sm = sm; this.cafe = cafe; this.rosterDate = rosterDate; this.status = status; this.punched = punched;
        this.checkInOnTime = checkInOnTime; this.checkOutOnTime = checkOutOnTime;
        this.checkInInLocation = checkInInLocation; this.checkOutInLocation = checkOutInLocation;
    }
}
