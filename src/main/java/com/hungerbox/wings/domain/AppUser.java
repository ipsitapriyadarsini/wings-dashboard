package com.hungerbox.wings.domain;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

/** Wings user. SMs/AMs are mapped to cafes; City Ops Heads to cities. */
@Entity
@Table(name = "app_user")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String name;
    private String email;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Role role;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_cafe", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "cafe_id"))
    private Set<Cafe> cafes = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_city", joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "city_id"))
    private Set<City> cities = new HashSet<>();

    protected AppUser() {}
    public AppUser(String name, String email, Role role) { this.name = name; this.email = email; this.role = role; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public Role getRole() { return role; }
    public Set<Cafe> getCafes() { return cafes; }
    public Set<City> getCities() { return cities; }
}
