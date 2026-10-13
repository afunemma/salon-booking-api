# ADR-0013: Dated time off for staff leave and salon closures

- **Status:** Accepted
- **Date:** 2026-10-13

## Context

Weekly schedules (ADR-0012) cover the usual week, but not one-off absences: a stylist's leave, a sick week, or the whole salon closed on a public holiday or over December. Like schedule changes, time off must never leave a client with a booking the salon won't honour.

## Decision

- **A `time_off` table:** a date range (`first_day`, `last_day`, both included) for one staff member, or for the whole salon when `staff_member_id` is `NULL`. One row covers "24–26 December", so there's no row per day.
- **The database guards it:** `CHECK (first_day <= last_day)`, and a composite foreign key `(staff_member_id, salon_id)` so time off can only name the salon's own staff. With `staff_member_id` `NULL`, PostgreSQL skips that check, which is exactly the "whole salon" case.
- **Free slots:** one query per request finds the time off that includes the date. A staff member it applies to gets no free times that day; a salon closure applies to everyone.
- **Adding time off over active bookings is refused** with `409 Schedule conflicts with bookings`, naming the first clash, through the same `ActiveBookings` interface as schedule changes (ADR-0012).
- **No race with bookings:** adding time off locks the salon row first, as booking does (ADR-0004). A client can't book a slot in the period between the clash check and the save. Schedule updates now take the same lock; they didn't before.
- **Owner-only API:** `POST`, `GET` (time off that hasn't ended) and `DELETE` on `/salons/{id}/time-off`. Clients don't see why a day has no free times, only that it has none. Removing time off can't strand anyone, so it is always allowed.
- **Limits:** time off that has already ended is rejected, and one entry can be at most 366 days.

## Consequences

- Owners can close for public holidays and record leave, and clients never land on a closed day.
- **No reason is stored.** "Sick" or "maternity leave" would be personal information (POPIA) that the app doesn't need.
- **No partial days** (e.g. leaving at 14:00). That would need a time range per entry; whole days cover the common cases.
- **No recurring closures** (every 25 December). The owner adds them each year.
- **The lock is not covered by a concurrency test** for this path; it is the same lock that `ConcurrentBookingTest` proves for bookings.

## Alternatives considered

- **One row per day off:** simple queries, but a two-week leave becomes 14 rows to add and remove together.
- **Salon closures as a separate table:** clearer naming, but the same columns and rules; a nullable staff member keeps one code path.
- **Cancel the clashing bookings automatically:** the salon decides on the clients' behalf without telling them. Refusing makes the owner cancel, which clients can see.
- **Public "closed" information for clients:** useful later for a front end; not needed for booking correctly.
