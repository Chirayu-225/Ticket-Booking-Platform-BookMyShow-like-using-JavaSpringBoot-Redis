-- ========================================================================
-- Users & roles
-- ========================================================================
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ========================================================================
-- Catalog: movies, venues, screens, shows, seats
-- ========================================================================
CREATE TABLE movies (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    language VARCHAR(50) NOT NULL,
    duration_minutes INT NOT NULL CHECK (duration_minutes > 0),
    poster_url VARCHAR(500),
    synopsis TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE venues (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    address VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_venues_city ON venues(city);

-- A screen's seat layout is rows x columns; individual seats are generated rows
-- in the `seats` table below (not stored as JSON) so each seat can be a real FK
-- target for holds and bookings.
CREATE TABLE screens (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL REFERENCES venues(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    total_rows INT NOT NULL CHECK (total_rows > 0),
    seats_per_row INT NOT NULL CHECK (seats_per_row > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE screen_seats (
    id BIGSERIAL PRIMARY KEY,
    screen_id BIGINT NOT NULL REFERENCES screens(id) ON DELETE CASCADE,
    row_label VARCHAR(5) NOT NULL,      -- 'A', 'B', ...
    seat_number INT NOT NULL,           -- 1, 2, 3, ...
    seat_tier VARCHAR(20) NOT NULL DEFAULT 'STANDARD' CHECK (seat_tier IN ('STANDARD', 'PREMIUM', 'RECLINER')),
    UNIQUE (screen_id, row_label, seat_number)
);

CREATE TABLE shows (
    id BIGSERIAL PRIMARY KEY,
    movie_id BIGINT NOT NULL REFERENCES movies(id) ON DELETE CASCADE,
    screen_id BIGINT NOT NULL REFERENCES screens(id) ON DELETE CASCADE,
    starts_at TIMESTAMPTZ NOT NULL,
    base_price NUMERIC(10, 2) NOT NULL CHECK (base_price > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_shows_movie ON shows(movie_id);
CREATE INDEX idx_shows_starts_at ON shows(starts_at);

-- One row per (show, screen_seat) — this is the thing concurrency is actually
-- protecting: exactly one of HELD or BOOKED may ever be true for a given row.
CREATE TABLE show_seats (
    id BIGSERIAL PRIMARY KEY,
    show_id BIGINT NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    screen_seat_id BIGINT NOT NULL REFERENCES screen_seats(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED')),
    version BIGINT NOT NULL DEFAULT 0,  -- optimistic-lock column read by JPA @Version
    UNIQUE (show_id, screen_seat_id)
);
CREATE INDEX idx_show_seats_show ON show_seats(show_id);

-- ========================================================================
-- Holds: the short-lived reservation while a user is paying.
-- Redis holds the authoritative TTL (SETNX + EX); this table is the durable,
-- queryable mirror so a crashed Redis node can never silently lose a hold's
-- existence from the booking history / admin views, and so the defensive
-- cleanup job (HoldCleanupJob) has something to sweep even if a Redis key
-- expired but the webhook never arrived.
-- ========================================================================
CREATE TABLE holds (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    show_id BIGINT NOT NULL REFERENCES shows(id),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    expires_at TIMESTAMPTZ NOT NULL,
    razorpay_order_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_holds_status_expires ON holds(status, expires_at);

CREATE TABLE hold_seats (
    hold_id BIGINT NOT NULL REFERENCES holds(id) ON DELETE CASCADE,
    show_seat_id BIGINT NOT NULL REFERENCES show_seats(id),
    PRIMARY KEY (hold_id, show_seat_id)
);

-- ========================================================================
-- Bookings: the durable, confirmed result of a successfully paid hold.
-- ========================================================================
CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    show_id BIGINT NOT NULL REFERENCES shows(id),
    hold_id BIGINT REFERENCES holds(id),
    total_amount NUMERIC(10, 2) NOT NULL,
    razorpay_payment_id VARCHAR(100) NOT NULL,
    -- the idempotency key from Razorpay's webhook payload: a UNIQUE constraint
    -- is what actually makes "an idempotent webhook" true, not just application logic
    idempotency_key VARCHAR(150) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_bookings_user ON bookings(user_id);

CREATE TABLE booking_seats (
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    show_seat_id BIGINT NOT NULL REFERENCES show_seats(id),
    PRIMARY KEY (booking_id, show_seat_id)
);
