package com.hungerbox.wings.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "cafe")
public class Cafe {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private City city;
    @ManyToOne(fetch = FetchType.LAZY)
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser areaManager;
    /** Excluded locations do not contribute to Cafe Score or SM Score (configurable). */
    private boolean scoreExcluded;
    private boolean active = true;

    protected Cafe() {}
    public Cafe(String name, City city, Company company, AppUser areaManager) {
        this.name = name; this.city = city; this.company = company; this.areaManager = areaManager;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public City getCity() { return city; }
    public Company getCompany() { return company; }
    public AppUser getAreaManager() { return areaManager; }
    public boolean isScoreExcluded() { return scoreExcluded; }
    public void setScoreExcluded(boolean scoreExcluded) { this.scoreExcluded = scoreExcluded; }
    public boolean isActive() { return active; }
}
