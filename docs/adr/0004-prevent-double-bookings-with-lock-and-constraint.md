# ADR-0004: Prevent double bookings with a row lock and an exclusion constraint

- **Status:** Accepted
- **Date:** 2026-09-30

## Context

Booking checks that a slot is free and then saves it. Two requests for the same slot at the same moment can both pass the check before either one saves: a check-then-act race condition. A test that sends 20 simultaneous requests showed **10 of them succeeding**.

## Decision

Two layers of defence:

1. **Pessimistic lock:** `BookingService.book()` first locks the salon's row (`SELECT ... FOR UPDATE`). Bookings for one salon run one at a time, so each check sees the previous booking.
2. **Exclusion constraint:** `booking_no_overlap` makes PostgreSQL reject any two active bookings in the same salon on the same date whose time ranges overlap. The service recognises this error by its SQL state (`23P01`) and constraint name, and returns `409 Slot unavailable`.

## Consequences

- Exactly one of many simultaneous requests succeeds. `ConcurrentBookingTest` proves this on every build.
- Even code that forgets the lock can't create overlapping bookings.
- All bookings for a salon wait for each other, even ones that can't clash (e.g. Monday and Friday). At salon scale this costs milliseconds.
- The design is PostgreSQL-specific (`btree_gist`, a custom `timerange` type).

## Alternatives considered

- **Exclusion constraint only:** stops double bookings, but under contention PostgreSQL reported deadlocks between the waiting inserts, and clients got server errors.
- **Optimistic locking (version column + retry):** works well when conflicts are rare, but needs retry logic, and a popular slot can cause many retries.
- **`synchronized` in Java:** only works inside one JVM. With two or more app instances the race comes back.
- **Finer-grained locks** (per staff member and date, via advisory locks): less waiting. Planned once staff members are added, which makes the staff member the natural resource to lock.
