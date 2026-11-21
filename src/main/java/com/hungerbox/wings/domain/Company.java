package com.hungerbox.wings.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "company")
public class Company {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;

    protected Company() {}
    public Company(String name) { this.name = name; }

    public Long getId() { return id; }
    public String getName() { return name; }
}
