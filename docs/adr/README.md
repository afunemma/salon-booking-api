# Architecture Decision Records

Short records of important design decisions: what was decided, why, and what it costs.
New decisions get the next number. If a decision is later replaced, its status becomes
"Superseded by ADR-00X"; it is never deleted, so the history stays readable.

| ADR | Decision | Status |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | Record architecture decisions | Accepted |
| [0002](0002-flyway-owns-the-database-schema.md) | Flyway owns the database schema | Accepted |
| [0003](0003-store-money-as-integer-cents.md) | Store money as integer cents, with optional price ranges | Accepted |
| [0004](0004-prevent-double-bookings-with-lock-and-constraint.md) | Prevent double bookings with a row lock and an exclusion constraint | Accepted |
| [0005](0005-package-by-feature-with-enforced-boundaries.md) | Package by feature, with boundaries enforced by tests | Accepted |
