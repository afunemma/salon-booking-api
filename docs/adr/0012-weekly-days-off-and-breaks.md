# ADR-0012: Weekly days off and daily breaks, without stranding bookings

- **Status:** Accepted
- **Date:** 2026-10-13

## Context

Staff members work the same hours every day (ADR-0011). Real salons close on some days, stylists have regular days off, and most people take a lunch break. The interview with the barber left working days, lunch and days off as open questions; every salon will need them.

Changing a schedule is risky once clients have booked: if an owner makes Monday a day off, a client already booked for Monday would turn up to a salon that no longer expects them.

## Decision

- **Working days per staff member,** stored as a bit mask in one `SMALLINT` column (`working_days`): Monday = 1, Tuesday = 2, … Sunday = 64, every day = 127. A `CHECK` requires at least one day. Existing staff default to every day, so nothing changes for them.
- **One optional daily break per staff member** (`break_starts_at`, `break_ends_at`). A `CHECK` requires both ends or neither, and the break must lie within working hours.
- **The rules live in plain Java:** `WorkSchedule` (days, hours, break) in the framework-free `scheduling` package. It answers "does this appointment fit?" and is unit-tested without Spring or a database.
- **Free slots:** no times on a day off. The break is passed to `SlotFinder` as if it were a booking, so the slot logic itself didn't change.
- **Schedule changes can't strand bookings.** Before saving a new schedule, the staff member's active bookings from today on are checked against it. If any would fall on a day off, outside the hours or in the break, the change is refused with `409 Schedule conflicts with bookings`, naming the first clash. The owner cancels those bookings first, or picks a schedule that keeps them.
- **Dependency inversion for that check:** the `booking` package depends on `salon`, so `salon` can't call booking code without a package cycle (forbidden by `ArchitectureTest`). Instead `salon` defines a small `ActiveBookings` interface, and `booking` implements it (`StaffActiveBookings`). Spring wires them together at runtime; at compile time the dependency still points one way.
- **`PUT` replaces the whole staff member:** left-out fields go back to their defaults (salon hours, every day, no break).

## Consequences

- Owners can model the usual week: closed Sundays, a stylist off Mondays, lunch 13:00–13:30.
- **Bookings stay honoured:** the only way to free a booked time is to cancel the booking, which is visible to the client through the cancel page.
- **The check reads all of a staff member's future active bookings.** At salon scale that's a few dozen rows; it would need a date limit for a much larger business.
- **One break a day, the same every working day.** Different hours per weekday need a schedule per weekday; the bit mask would become a small table.
- **No dated time off yet** (holidays, leave, a salon closed on a public holiday). That's the next step.
- **The bit mask is compact but not self-explanatory in SQL,** so the migration and `WorkSchedule.toMask` document it.

## Alternatives considered

- **A `staff_working_day` table (one row per day):** clearer in SQL, but an extra join or collection fetch on every free-slots request, for 7 possible values.
- **A PostgreSQL array or a text list ("MON,TUE"):** readable, but needs custom JPA mapping or string parsing; the bit mask maps to a plain `short`.
- **Silently cancel or move stranded bookings:** clients would lose bookings without being asked. Refusing makes the owner decide.
- **Warn but save anyway:** an API warning is easy to miss, and the client still turns up to a closed salon.
- **Let `salon` depend on `booking` directly:** creates a package cycle, so neither package can be understood or changed on its own (ADR-0005).
