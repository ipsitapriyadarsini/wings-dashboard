package com.hungerbox.wings.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "vendor")
public class Vendor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;

    protected Vendor() {}
    public Vendor(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }
}
