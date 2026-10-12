package com.ticketbooking.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * The durable mirror of a Redis seat hold. Redis (SETNX + EX) is the
 * fast, authoritative lock that actually prevents two users from both
 * thinking they hold the same seat; this table exists so a hold is still
 * visible to the admin panel, booking history, and the defensive
 * HoldCleanupJob even if the Redis key has already expired or Redis itself
 * restarted.
 */
@Entity
@Table(name = "holds")
public class Hold {

    public enum Status { ACTIVE, CONFIRMED, EXPIRED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "razorpay_order_id")
    private String razorpayOrderId;

    /** Random per-hold value stored in this hold's Redis seat keys; see HoldService. */
    @Column(name = "lock_token", length = 64)
    private String lockToken;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ManyToMany
    @JoinTable(
            name = "hold_seats",
            joinColumns = @JoinColumn(name = "hold_id"),
            inverseJoinColumns = @JoinColumn(name = "show_seat_id"))
    private Set<ShowSeat> seats = new HashSet<>();

    protected Hold() {}

    public Hold(User user, Show show, Instant expiresAt) {
        this(user, show, expiresAt, null);
    }

    public Hold(User user, Show show, Instant expiresAt, String lockToken) {
        this.user = user;
        this.show = show;
        this.expiresAt = expiresAt;
        this.lockToken = lockToken;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Show getShow() { return show; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
    public Set<ShowSeat> getSeats() { return seats; }
    public String getLockToken() { return lockToken; }
    public Instant getCreatedAt() { return createdAt; }
}
