package com.ticketbooking.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "shows")
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Show() {}

    public Show(Movie movie, Screen screen, Instant startsAt, BigDecimal basePrice) {
        this.movie = movie;
        this.screen = screen;
        this.startsAt = startsAt;
        this.basePrice = basePrice;
    }

    public Long getId() { return id; }
    public Movie getMovie() { return movie; }
    public Screen getScreen() { return screen; }
    public Instant getStartsAt() { return startsAt; }
    public BigDecimal getBasePrice() { return basePrice; }
}
