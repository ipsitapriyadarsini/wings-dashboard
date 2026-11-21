package com.hungerbox.wings.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

/** A checklist instance mapped to a cafe for a day. Its questions live in {@link ChecklistResponse}. */
@Entity
@Table(name = "checklist", indexes = @Index(name = "ix_checklist_cafe_date", columnList = "cafe_id,checklist_date"))
public class Checklist {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cafe_id")
    private Cafe cafe;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "vendor_id")
    private Vendor vendor;
    @Column(name = "checklist_date", nullable = false)
    private LocalDate checklistDate;
    private String name;

    protected Checklist() {}
    public Checklist(Cafe cafe, Vendor vendor, LocalDate checklistDate, String name) {
        this.cafe = cafe; this.vendor = vendor; this.checklistDate = checklistDate; this.name = name;
    }

    public Long getId() { return id; }
}
