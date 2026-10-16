# ADR-0016: Limit upcoming bookings per phone number

- **Status:** Accepted
- **Date:** 2026-10-16

## Context

Clients book without an account, through the API or the booking page. Nothing stopped one person filling a salon's diary with fake bookings, so real clients would find no free times.

A real client rarely holds more than one or two upcoming appointments at one salon, though a parent may book for several children.

The same number can be typed many ways: `082 123 4567`, `0821234567`, `+27 82 123 4567`. A plain text comparison would see three different clients.

## Decision

- **At most 3 upcoming bookings per phone number per salon,** configurable as `app.booking.max-upcoming-per-phone`. "Upcoming" means still booked and from today on, so past, cancelled, completed and no-show bookings don't count. Cancelling one frees a place.
- **A phone key for comparing numbers:** each booking stores the last 9 digits of the number (`client_phone_key`). All three ways of writing a South African number give `821234567`. The number as typed is kept for display.
- **An index on `(salon_id, client_phone_key)`** keeps the count fast.
- **Checked after the salon lock** (ADR-0004), together with the free-slot check, so two requests at the same moment can't both slip under the limit.
- **`409 Too many bookings`** with an explanation; the booking page shows it on the form. Rejections are counted as `salon_bookings_rejected_total{reason="too_many_for_phone"}`.
- **Migration V8 fills in the key for existing bookings** with the same rule in SQL; a test checks the SQL and Java rules agree.

## Consequences

- One number can no longer fill a diary.
- **Someone can still invent numbers,** because numbers aren't verified. A per-IP limit for bookings (like the login limit, ADR-0007) would be the next layer; verifying numbers by SMS or WhatsApp code would be the strongest, at a cost per booking and some friction.
- **The 9-digit key can make two foreign numbers look the same** if their last 9 digits match. That's unlikely among one salon's clients, and the worst case is a client being refused a fourth booking.
- **Families with more than 3 children at once** need to call the salon, or the salon raises the limit.

## Alternatives considered

- **Store the number in international form** (`27821234567`): exact, but needs a country code for every number and changes what owners see.
- **Compare with `regexp_replace` in each query:** no new column, but no index, and the rule would live in SQL strings instead of one Java method.
- **Limit per IP address only:** stops one machine, but not someone using mobile data, where many people share an IP; and it doesn't express the business rule.
- **Require SMS verification:** strongest, but costs money per booking and adds a step that would put clients off.
