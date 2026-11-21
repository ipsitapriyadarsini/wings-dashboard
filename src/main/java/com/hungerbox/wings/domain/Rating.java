package com.hungerbox.wings.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "rating", indexes = @Index(name = "ix_rating_cafe_date", columnList = "cafe_id,rated_on"))
public class Rating {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cafe_id")
    private Cafe cafe;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vendor_id")
    private Vendor vendor;
    @Column(name = "rated_on", nullable = false)
    private LocalDate ratedOn;
    @Column(nullable = false)
    private int stars;
    private String issue;
    private String subIssue;

    protected Rating() {}
    public Rating(Cafe cafe, Vendor vendor, LocalDate ratedOn, int stars, String issue, String subIssue) {
        this.cafe = cafe; this.vendor = vendor; this.ratedOn = ratedOn; this.stars = stars;
        this.issue = issue; this.subIssue = subIssue;
    }
}
