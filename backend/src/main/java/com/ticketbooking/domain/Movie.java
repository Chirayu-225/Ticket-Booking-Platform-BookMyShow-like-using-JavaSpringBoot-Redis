package com.ticketbooking.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "movies")
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String language;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "poster_url")
    private String posterUrl;

    @Column(columnDefinition = "TEXT")
    private String synopsis;

    private String genre;

    private String certificate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Movie() {}

    public Movie(String title, String language, int durationMinutes, String posterUrl, String synopsis) {
        this(title, language, durationMinutes, posterUrl, synopsis, null, null);
    }

    public Movie(String title, String language, int durationMinutes, String posterUrl, String synopsis,
                 String genre, String certificate) {
        this.title = title;
        this.language = language;
        this.durationMinutes = durationMinutes;
        this.posterUrl = posterUrl;
        this.synopsis = synopsis;
        this.genre = genre;
        this.certificate = certificate;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getLanguage() { return language; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getPosterUrl() { return posterUrl; }
    public String getSynopsis() { return synopsis; }
    public String getGenre() { return genre; }
    public String getCertificate() { return certificate; }
}
