package com.ticketbooking.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hold_id")
    private Hold hold;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "razorpay_payment_id", nullable = false)
    private String razorpayPaymentId;

    // Unique constraint (see V1 migration) is what makes the webhook genuinely
    // idempotent: a retried delivery with the same key hits a constraint
    // violation, caught and treated as "already processed", not re-booked.
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @ManyToMany
    @JoinTable(
            name = "booking_seats",
            joinColumns = @JoinColumn(name = "booking_id"),
            inverseJoinColumns = @JoinColumn(name = "show_seat_id"))
    private Set<ShowSeat> seats = new HashSet<>();

    protected Booking() {}

    public Booking(User user, Show show, Hold hold, BigDecimal totalAmount,
                    String razorpayPaymentId, String idempotencyKey) {
        this.user = user;
        this.show = show;
        this.hold = hold;
        this.totalAmount = totalAmount;
        this.razorpayPaymentId = razorpayPaymentId;
        this.idempotencyKey = idempotencyKey;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Show getShow() { return show; }
    public Hold getHold() { return hold; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getRazorpayPaymentId() { return razorpayPaymentId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Set<ShowSeat> getSeats() { return seats; }
}
