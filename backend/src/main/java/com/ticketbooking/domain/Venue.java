package com.ticketbooking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "venues")
public class Venue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    private String address;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Venue() {}

    public Venue(String name, String city, String address) {
        this.name = name;
        this.city = city;
        this.address = address;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getAddress() { return address; }
}
