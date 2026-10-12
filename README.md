# Ticket Booking

A BookMyShow-style seat booking system: browse showtimes, pick seats on a
live seat map, hold them while you pay, and get a confirmed booking — built
to answer one classic interview question in working code:
**how do you stop 1,000 users from booking the same seat?**

The whole UI is [Vaadin Flow](https://vaadin.com/flow) — Java pages and a
hand-built seat-map component — running in the same JVM as the Spring Boot
backend. One click on a seat is a direct Java method call, not a network
request: no separate frontend, no CORS, no React, one deployable JAR.

## Stack

| Layer | Choice |
| --- | --- |
| Backend + UI | Java 21, Spring Boot 3.4, Vaadin Flow 24 (one process, React/Hilla excluded) |
| Database | PostgreSQL + Flyway migrations |
| Seat holds | Redis (`SET NX EX` with per-hold tokens) |
| Payments | Razorpay test mode (Orders API, checkout callback + webhook) |
| Real-time | Vaadin server push |
| Tests | JUnit 5, Testcontainers (Postgres + Redis), a 50-thread race test |

The only JavaScript in the product is Razorpay's `checkout.js` (the payment
popup only Razorpay can host), driven from Java via `executeJs`.

## Running locally

```bash
cd backend
docker compose up -d          # Postgres on host port 5433, Redis on 6379
copy .env.example .env        # then put your Razorpay test keys in .env
run.bat                       # Windows: loads .env, runs mvn spring-boot:run
```

Open http://localhost:8080 — demo login `demo@ticketbooking.dev` /
`password123`, admin `admin@ticketbooking.dev` / `password123`.

Flyway applies the schema and demo catalog on startup, and a small demo
scheduler keeps showtimes filled for the next few days so the catalog never
goes stale (`DEMO_AUTO_SCHEDULE=false` turns it off).

The first start after a dependency change takes a few minutes while Vaadin
installs frontend packages. Theme edits in
`src/main/frontend/themes/ticketbooking/styles.css` then show up on a
browser refresh (`vaadin.frontend.hotdeploy=true`).

### Razorpay test mode

1. Sign up at razorpay.com. Onboarding asks for a PAN before the dashboard
   unlocks; test mode itself needs no further KYC documents.
2. Dashboard → Account & Settings → API Keys → generate **Test Mode** keys
   and put them in `backend/.env` (`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`).
3. Pay with Razorpay's test cards / test UPI ids. No real money moves.

That is enough for the full flow locally: when the checkout popup reports
success, the browser hands Razorpay's signed response to the server, which
verifies the HMAC signature and books the seats.

The **webhook** (`POST /api/webhooks/razorpay`, event `payment.captured`)
is the backstop for users who close the tab right after paying. It needs a
public URL (your Render deployment, or ngrok locally) registered in the
dashboard, plus `RAZORPAY_WEBHOOK_SECRET`. Both paths are idempotent on the
Razorpay payment id, so whichever arrives second is a no-op.

## How the concurrency works

1. **Redis, first line.** Holding seats runs `SET seat-lock:{id} <token> NX EX 600`
   per seat. Of N simultaneous requests for a seat, exactly one gets the key;
   the rest are rejected without touching Postgres. All-or-nothing: if any
   seat fails, the keys this request took are given back.
2. **Postgres, source of truth.** The winner then row-locks the seats
   (`SELECT … FOR UPDATE`, in id order so concurrent lockers can't deadlock)
   and re-checks them: a `BOOKED` seat is never re-held, even if Redis lost
   every key.
3. **Owned locks.** Each hold's Redis keys carry a random token stored on the
   hold. Locks are released with a Lua compare-and-delete, so cleaning up an
   expired hold can never delete a lock a newer hold now owns.
4. **Stale holds.** If a hold's Redis TTL lapsed but the cleanup sweep
   hasn't run yet, the next person to take the seat retires the old hold in
   the same transaction — so a late payment for it can't also book the seat.
5. **Payment confirmation.** Seats are re-locked, checked, flipped to
   `BOOKED`, and the booking is inserted in one transaction. The idempotency
   key (payment id) is `UNIQUE`, and `booking_seats.show_seat_id` is `UNIQUE`:
   the database itself refuses to sell one seat twice.
6. **Side effects after commit.** Releasing Redis locks and pushing seat-map
   updates to other browsers happen only after the transaction commits.

`ConcurrentSeatHoldTest` fires 50 threads at one seat and asserts exactly one
hold exists afterwards. `BookingIdempotencyTest` reports the same payment
twice (one booking) and a second payment for a paid hold (refused).

## Deploying for free

- **Render** — the JAR from `mvn package -Pproduction` (see `backend/Dockerfile`).
- **Neon** — managed Postgres. **Upstash** — managed Redis.
- No separate frontend host.

## Known limits

- `SeatStatusBroadcaster` is in-memory, so live seat updates assume a single
  app instance; more instances would need Redis pub/sub.
- Refunds are logged (`REFUND NEEDED`), not issued via Razorpay's refund API.
