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
| [0006](0006-stateless-jwt-auth-for-owners-only.md) | Stateless JWT login for salon owners; clients book without an account | Accepted |
| [0007](0007-in-memory-login-rate-limiting.md) | Rate-limit login attempts in memory | Accepted |
| [0008](0008-free-hosting-on-render-and-neon.md) | Host the live demo on Render and Neon free plans | Accepted |
| [0009](0009-evening-reminders-with-a-scheduled-job.md) | Send evening-before reminders with a scheduled job | Accepted |
| [0010](0010-signed-cancel-links-for-clients.md) | Let clients cancel from a signed link | Accepted |
| [0011](0011-staff-members-with-own-hours.md) | Staff members with their own hours, and overlaps per staff member | Accepted |
| [0012](0012-weekly-days-off-and-breaks.md) | Weekly days off and daily breaks, without stranding bookings | Accepted |
| [0013](0013-dated-time-off.md) | Dated time off for staff leave and salon closures | Accepted |
| [0014](0014-whatsapp-reminders-through-meta-cloud-api.md) | Deliver reminders by WhatsApp through Meta's Cloud API, switched off by default | Accepted |
| [0015](0015-one-platform-whatsapp-number-for-now.md) | Send reminders from one platform WhatsApp number for now, not each salon's own | Accepted |
| [0016](0016-limit-upcoming-bookings-per-phone-number.md) | Limit upcoming bookings per phone number | Accepted |
| [0017](0017-limit-booking-attempts-per-ip-address.md) | Limit booking attempts per IP address | Accepted |
| [0018](0018-optimistic-locking-for-booking-changes.md) | Optimistic locking for changes to existing bookings | Accepted |
| [0019](0019-session-login-for-owner-pages.md) | Session login with CSRF protection for the owner pages | Accepted |
