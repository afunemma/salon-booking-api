# ADR-0018: Optimistic locking for changes to existing bookings

- **Status:** Accepted
- **Date:** 2026-10-19

## Context

Two people can change the same booking at the same moment: the owner marks it done or a no-show while the client cancels it from their link. Both requests read the booking as `BOOKED`, both pass the "is it still booked?" check, and the later save silently overwrites the earlier one (a **lost update**). The salon could record a client as having come when they had cancelled.

New bookings are already protected by the salon row lock and the exclusion constraint (ADR-0004). Changes to existing bookings weren't.

## Decision

- **A `version` column on `booking`** (Flyway V9) and JPA's `@Version` on the entity. Every update becomes `UPDATE … SET …, version = version + 1 WHERE id = ? AND version = ?`. If someone else changed the booking since it was read, no row matches and the update fails.
- **The API returns `409 Changed at the same time`:** "Someone else changed this at the same moment. Reload and try again." `ApiExceptionHandler` maps Spring's `OptimisticLockingFailureException`.
- **The client's cancel page reloads** instead of showing an error, so the client sees what the booking is now (e.g. "can't be cancelled online any more").
- **Tested as a real race:** `ConcurrentStatusChangeTest` uses two threads and latches to force the worst order against PostgreSQL. The test fails when `@Version` is removed.

## Consequences

- **Lost updates on bookings are impossible:** the second of two simultaneous changes is refused, and the first is kept.
- **No locks while reading:** conflicts are rare (two people pressing a button on the same booking within milliseconds), so detecting them is cheaper than locking every read.
- **The losing request must be retried by a person,** after a reload. For a status change that's the right outcome: the retry is checked against the new state.
- **Only within the server, not across HTTP requests.** If an owner's app loaded a booking minutes ago and sends a change now, nothing compares versions. Returning the version as an `ETag` and requiring `If-Match` would cover that, if an owner app ever needs it.
- **Only bookings have a version,** because they're the one record both clients and owners change. Staff, services and time off are changed by the owner alone.

## Alternatives considered

- **A row lock (`SELECT … FOR UPDATE`) on every status change:** also correct, but every change would take a lock and make other changes to that booking wait, to prevent a rare clash. (Plain reads aren't blocked: PostgreSQL's MVCC lets a `SELECT` read the last committed row without waiting for the lock.)
- **Lock the salon row,** as booking does: correct, but serialises unrelated changes across the whole salon.
- **Last write wins:** simplest, and the bug this fixes.
