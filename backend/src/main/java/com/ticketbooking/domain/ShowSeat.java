package com.ticketbooking.domain;

import jakarta.persistence.*;

/**
 * One row per (show, physical seat). This is the row every concurrency
 * guarantee in the project is actually about: PostgreSQL's row-level
 * pessimistic lock (SELECT ... FOR UPDATE, taken in BookingService when a
 * webhook confirms payment) and the @Version optimistic-lock column both
 * apply here, as a defense-in-depth backstop behind the Redis hold.
 */
@Entity
@Table(name = "show_seats", uniqueConstraints = @UniqueConstraint(columnNames = {"show_id", "screen_seat_id"}))
public class ShowSeat {

    public enum Status { AVAILABLE, HELD, BOOKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_seat_id", nullable = false)
    private ScreenSeat screenSeat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.AVAILABLE;

    @Version
    private long version;

    protected ShowSeat() {}

    public ShowSeat(Show show, ScreenSeat screenSeat) {
        this.show = show;
        this.screenSeat = screenSeat;
    }

    public Long getId() { return id; }
    public Show getShow() { return show; }
    public ScreenSeat getScreenSeat() { return screenSeat; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public long getVersion() { return version; }
}
