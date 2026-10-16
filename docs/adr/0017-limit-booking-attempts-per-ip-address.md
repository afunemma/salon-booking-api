# ADR-0017: Limit booking attempts per IP address

- **Status:** Accepted
- **Date:** 2026-10-16

## Context

The limit per phone number (ADR-0016) stops one number filling a diary, but phone numbers aren't verified: someone could invent a new number for every booking. Login already has a per-IP limit (ADR-0007), with token buckets kept in memory.

Many mobile users in South Africa share one public IP address (carrier-grade NAT), so a tight per-IP limit would block real clients on the same network.

## Decision

- **At most 20 booking attempts per IP address per hour** (`app.booking.bookings-per-ip`). Every attempt counts, including ones refused because the slot was taken, so hammering the endpoint is limited too.
- **Checked first in `BookingService.book`,** before the salon lock and any database work, so the API and the booking page share one rule and a refused attempt costs almost nothing.
- **`429 Too Many Requests` with `Retry-After`** from the API; the booking page shows the message on the form, with the same status and header. Refusals are counted as `salon_bookings_rejected_total{reason="rate_limited_ip"}`.
- **One rate limiter for the whole app:** the token-bucket code from the login limiter moved into `common.KeyedRateLimiter`, which login and booking both use. Its behaviour didn't change, and the existing login limiter tests still pass.
- **The real client IP** comes from Render's proxy header, trusting only proxies on internal networks (ADR-0008), so clients can't fake their IP to get around the limit.

## Consequences

- One machine can make at most 20 attempts an hour, even with invented numbers. Combined with 3 per number (ADR-0016), filling a diary now needs many machines.
- **Shared IPs share the limit:** a busy office or mobile network could, in theory, hit 20 attempts an hour; then clients wait or call the salon. The limit is a setting if that happens.
- **Counts are per app instance and reset on restart,** as with login (ADR-0007). A shared store such as Redis would be needed for exact limits across instances.
- **A determined attacker with many IP addresses** still gets through. Verifying numbers by SMS or WhatsApp code would be the next step, at a cost per booking (ADR-0016).

## Alternatives considered

- **Count only successful bookings:** fairer to clients who keep finding slots taken, but leaves the endpoint open to hammering.
- **A CAPTCHA on the booking page:** effective against bots, but adds friction for every client and doesn't protect the API.
- **Rate limiting at a gateway or CDN** (e.g. Cloudflare): good to add in front later, but the app shouldn't rely on it alone.
