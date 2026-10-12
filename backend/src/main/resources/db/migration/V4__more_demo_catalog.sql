-- More (fictional) demo catalog. Shows themselves are not seeded here:
-- DemoScheduleJob creates upcoming showtimes on startup and nightly, so the
-- catalog never goes stale the way fixed dates in a migration would.

UPDATE movies SET genre = 'Mystery · Drama', certificate = 'UA' WHERE title = 'Pathless Woods';
UPDATE movies SET genre = 'Heist · Thriller', certificate = 'UA' WHERE title = 'Monsoon Protocol';

INSERT INTO movies (title, language, duration_minutes, poster_url, synopsis, genre, certificate) VALUES
    ('Saffron Skies', 'Hindi', 136, NULL, 'Two estranged sisters reopen their late father''s kite workshop in old Jaipur.', 'Family · Drama', 'U'),
    ('Kaveri Express', 'Kannada', 151, NULL, 'A night train, a missing ledger and a ticket collector who notices everything.', 'Action · Thriller', 'UA'),
    ('Last Light at Hampi', 'English', 118, NULL, 'An archaeologist races the monsoon to decode a carving no one else can see.', 'Adventure', 'UA'),
    ('The Quiet Algorithm', 'English', 124, NULL, 'A startup''s recommendation engine starts recommending its own founders'' secrets.', 'Sci-Fi · Thriller', 'A'),
    ('Chai & Chaos', 'Tamil', 112, NULL, 'One tea stall, one wedding and far too many relatives.', 'Comedy', 'U');

INSERT INTO venues (name, city, address) VALUES
    ('Lumiere Cinemas', 'Bengaluru', '100 Feet Road, Indiranagar, Bengaluru');

INSERT INTO screens (venue_id, name, total_rows, seats_per_row)
SELECT id, 'Audi 1', 7, 14 FROM venues WHERE name = 'Lumiere Cinemas';

DO $$
DECLARE
    v_screen BIGINT;
    r INT;
    c INT;
BEGIN
    SELECT s.id INTO v_screen
    FROM screens s JOIN venues v ON v.id = s.venue_id
    WHERE v.name = 'Lumiere Cinemas' AND s.name = 'Audi 1';

    FOR r IN 1..7 LOOP
        FOR c IN 1..14 LOOP
            INSERT INTO screen_seats (screen_id, row_label, seat_number, seat_tier)
            VALUES (v_screen, chr(64 + r), c,
                    CASE WHEN r <= 2 THEN 'RECLINER' WHEN r <= 4 THEN 'PREMIUM' ELSE 'STANDARD' END);
        END LOOP;
    END LOOP;
END $$;
