-- ========================================================================
-- Correctness guards added after the first end-to-end run.
-- ========================================================================

-- Every hold gets a random token, and that token is the value stored in the
-- hold's Redis keys. Releasing a hold deletes a key ONLY if it still carries
-- this hold's token, so an expired hold being cleaned up can never delete a
-- lock that a newer hold has since taken on the same seat.
ALTER TABLE holds ADD COLUMN lock_token VARCHAR(64);

-- The final backstop for "never sell the same seat twice": a show_seat row
-- (one seat for one show) can appear in at most one booking, enforced by the
-- database itself regardless of what the application code does.
ALTER TABLE booking_seats
    ADD CONSTRAINT uq_booking_seats_show_seat UNIQUE (show_seat_id);

-- One Razorpay order belongs to exactly one hold (webhooks look holds up by it).
CREATE UNIQUE INDEX uq_holds_razorpay_order
    ON holds (razorpay_order_id) WHERE razorpay_order_id IS NOT NULL;

CREATE INDEX idx_hold_seats_show_seat ON hold_seats (show_seat_id);
CREATE INDEX idx_holds_user ON holds (user_id);

-- Richer catalog metadata for the browse page.
ALTER TABLE movies ADD COLUMN genre VARCHAR(100);
ALTER TABLE movies ADD COLUMN certificate VARCHAR(10);
