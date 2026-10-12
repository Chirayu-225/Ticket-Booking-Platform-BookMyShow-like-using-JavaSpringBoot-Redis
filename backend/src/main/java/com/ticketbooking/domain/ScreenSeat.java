package com.ticketbooking.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "screen_seats", uniqueConstraints = @UniqueConstraint(columnNames = {"screen_id", "row_label", "seat_number"}))
public class ScreenSeat {

    public enum Tier { STANDARD, PREMIUM, RECLINER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(name = "row_label", nullable = false, length = 5)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_tier", nullable = false)
    private Tier tier = Tier.STANDARD;

    protected ScreenSeat() {}

    public ScreenSeat(Screen screen, String rowLabel, int seatNumber, Tier tier) {
        this.screen = screen;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.tier = tier;
    }

    public Long getId() { return id; }
    public Screen getScreen() { return screen; }
    public String getRowLabel() { return rowLabel; }
    public int getSeatNumber() { return seatNumber; }
    public Tier getTier() { return tier; }
    public String getLabel() { return rowLabel + seatNumber; }
}
