package com.ticketbooking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "screens")
public class Screen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(nullable = false)
    private String name;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "seats_per_row", nullable = false)
    private int seatsPerRow;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Screen() {}

    public Screen(Venue venue, String name, int totalRows, int seatsPerRow) {
        this.venue = venue;
        this.name = name;
        this.totalRows = totalRows;
        this.seatsPerRow = seatsPerRow;
    }

    public Long getId() { return id; }
    public Venue getVenue() { return venue; }
    public String getName() { return name; }
    public int getTotalRows() { return totalRows; }
    public int getSeatsPerRow() { return seatsPerRow; }
}
