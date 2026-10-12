-- Demo catalog so the app has something to browse immediately after first deploy.
-- Purely illustrative data for a portfolio project — not meant to be exhaustive.

INSERT INTO users (email, password_hash, full_name, role) VALUES
    -- password for both demo accounts is "password123" (BCrypt-hashed below)
    ('admin@ticketbooking.dev', '$2b$10$K8L32WhDGhyQ6vv5lzBcB./9V8AnJeuIbZa7jpRcw3zYKxwyNQZTS', 'Admin User', 'ADMIN'),
    ('demo@ticketbooking.dev', '$2b$10$K8L32WhDGhyQ6vv5lzBcB./9V8AnJeuIbZa7jpRcw3zYKxwyNQZTS', 'Demo User', 'USER');

INSERT INTO movies (title, language, duration_minutes, poster_url, synopsis) VALUES
    ('Pathless Woods', 'Kannada', 142, NULL, 'A detective drama set across Bengaluru''s tech corridor.'),
    ('Monsoon Protocol', 'Hindi', 128, NULL, 'A heist thriller that unfolds over one rainy night.');

INSERT INTO venues (name, city, address) VALUES
    ('PVR Forum Mall', 'Bengaluru', 'Koramangala, Bengaluru'),
    ('INOX Garuda Mall', 'Bengaluru', 'Magrath Road, Bengaluru');

INSERT INTO screens (venue_id, name, total_rows, seats_per_row) VALUES
    (1, 'Screen 1', 8, 10),
    (2, 'Screen A', 6, 12);

-- Generate screen_seats for both screens programmatically via a small PL/pgSQL block,
-- since hand-writing 80+ INSERT rows is exactly the kind of thing a migration should avoid.
DO $$
DECLARE
    r INT;
    c INT;
    row_char TEXT;
BEGIN
    FOR r IN 1..8 LOOP
        row_char := chr(64 + r); -- A, B, C, ...
        FOR c IN 1..10 LOOP
            INSERT INTO screen_seats (screen_id, row_label, seat_number, seat_tier)
            VALUES (1, row_char, c, CASE WHEN r <= 2 THEN 'RECLINER' WHEN r <= 5 THEN 'PREMIUM' ELSE 'STANDARD' END);
        END LOOP;
    END LOOP;
    FOR r IN 1..6 LOOP
        row_char := chr(64 + r);
        FOR c IN 1..12 LOOP
            INSERT INTO screen_seats (screen_id, row_label, seat_number, seat_tier)
            VALUES (2, row_char, c, CASE WHEN r <= 1 THEN 'RECLINER' WHEN r <= 3 THEN 'PREMIUM' ELSE 'STANDARD' END);
        END LOOP;
    END LOOP;
END $$;

INSERT INTO shows (movie_id, screen_id, starts_at, base_price) VALUES
    (1, 1, now() + interval '1 day' + interval '19 hours', 280.00),
    (1, 1, now() + interval '2 days' + interval '19 hours', 280.00),
    (2, 2, now() + interval '1 day' + interval '21 hours', 240.00);

-- Every seat on a screen gets a show_seats row, one per scheduled show, starting AVAILABLE.
INSERT INTO show_seats (show_id, screen_seat_id, status)
SELECT s.id, ss.id, 'AVAILABLE'
FROM shows s
JOIN screen_seats ss ON ss.screen_id = s.screen_id;
