# ADR-0011: Staff members with their own hours, and overlaps per staff member

- **Status:** Accepted
- **Date:** 2026-10-13

## Context

Until now, a salon could serve one client at a time: overlaps were prevented per salon (ADR-0004). That fits the interviewed solo barber, but the product is for all salon types, and a hair salon with three stylists serves three clients at once. Stylists also keep different hours.

The solo barber must not get extra set-up work: he felt a solo barber would struggle to manage an app.

## Decision

- **A `staff_member` table:** each staff member belongs to one salon and has daily working hours (`starts_at`, `ends_at`) within the salon's opening hours.
- **Every salon starts with one staff member,** "Owner", working the salon's hours. A solo salon never has to set anything up, and its free slots and bookings behave exactly as before. The owner can rename it.
- **Every booking is with one staff member** (`booking.staff_member_id`, required).
- **The exclusion constraint moves from salon to staff member:** `EXCLUDE USING gist (staff_member_id WITH =, booking_date WITH =, timerange(...) WITH &&) WHERE (status = 'BOOKED')`. Two stylists can both start at 10:00. One stylist still can't be double-booked.
- **A composite foreign key** `(staff_member_id, salon_id) → staff_member (id, salon_id)` makes the database reject a booking with another salon's staff member, whatever the Java code does.
- **The salon row lock stays** (ADR-0004). It still orders bookings for one salon, so "any staff member" choices can't race each other.
- **Clients may choose:** `staffId` is optional when booking and on `free-slots`. Without it, `free-slots` lists times when *anyone* is free, and a booking goes to the first free staff member in the order they were added.
- **The API only adds fields** (`staffId` and `staffName` on bookings, `staffId` on free slots), so existing clients keep working.
- **The upgrade is safe for existing data:** migration V5 gives each existing salon an "Owner" staff member with the salon's hours and moves its bookings to them, all in one migration.

## Consequences

- **Larger salons can take parallel bookings,** and each stylist's hours are respected.
- **"First free staff member" isn't balanced:** the owner fills up first. Fine for small salons; a round-robin or least-busy rule could replace it later without API changes.
- **Every staff member can do every service.** Salons where only some staff do braids would need a staff–service link.
- **Hours are the same every day.** Days off and lunch breaks are not modelled yet, for the salon or for staff.
- **The salon lock serialises bookings across all of a salon's staff.** Still only a few bookings a minute per salon, so not a bottleneck.

## Alternatives considered

- **Keep overlaps per salon and model staff only as a label:** simpler, but a 3-stylist salon could still only take one client at a time.
- **Lock per staff member instead of per salon:** more parallelism, but "any staff member" bookings would need to lock several rows in a fixed order to avoid deadlocks. Not worth it at salon scale.
- **Make clients always choose a staff member:** extra friction for the common case where clients don't mind, and pointless in a solo salon.
- **Weekly schedules (per weekday) now:** more realistic, but a bigger model and API. Daily hours cover the interviewed barber and are a base to extend.
