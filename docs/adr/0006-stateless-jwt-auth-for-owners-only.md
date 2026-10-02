# ADR-0006: Stateless JWT login for salon owners; clients book without an account

- **Status:** Accepted
- **Date:** 2026-10-02

## Context

Before this change anyone could call any endpoint: create salons, read every salon's client names and phone numbers, and cancel any booking by guessing its id.

Customer research showed the barber's clients book by WhatsApp, phone calls and walk-ins. Making them create an account would add friction and lose bookings. Owners, on the other hand, must be the only ones who can manage their salon.

## Decision

- **Who needs to log in:**
  - **Owners log in; clients don't.** Viewing salons, services and free slots, and making a booking, stay public.
  - Everything else needs a login token.
- **Tokens:**
  - **Stateless JWTs** signed with HMAC-SHA256.
  - Created and checked with **Spring Security's OAuth 2 resource server** support (Nimbus), not hand-written token code.
  - Tokens hold only the user id, last 1 hour, and are checked for signature, expiry and issuer.
- **Signing key:**
  - **From the environment** (`APP_SECURITY_JWT_SECRET`, at least 32 characters), never committed.
  - Locally a random key is generated at startup, with a warning.
- **Ownership:**
  - **Checked in the service layer.** `SalonService.findOwnedSalon(salonId, userId)` is the single gate for every owner-only operation and returns `403 Forbidden` for other users.
  - Salons store their owner as an id (`owner_id`), not an entity reference, so the `salon` package doesn't depend on `account`.
- **Passwords:**
  - **BCrypt**, through Spring's delegating encoder, so the algorithm can be upgraded later.
  - 12 to 72 characters (BCrypt ignores anything after 72 bytes).
- **No account enumeration at login:**
  - A wrong password and an unknown email give an identical response.
  - Unknown emails are still checked against a dummy hash, so the response time doesn't reveal which emails exist.

## Consequences

- Security is proven by `SecurityIntegrationTest`: missing, garbage, expired and forged tokens, and one owner trying to read or change another owner's salon.
- **Logout and revocation:** no server-side sessions makes the app easy to scale, but a token can't be revoked before it expires. The short 1-hour lifetime limits the damage. Refresh tokens or a deny-list would come later if needed.
- **Registration reveals existing emails:** `409 Email already registered` tells a caller an email exists. This trade-off is common, and could be removed with email verification.
- **Rotating the signing key** logs everyone out.
- **Clients can't cancel their own bookings yet.** A future change could send them a cancellation link containing a single-use token.
- **No brute-force protection on login yet** (rate limiting). This is planned with the deployment step.

## Alternatives considered

- **Server-side sessions with cookies:** simple, but needs CSRF protection and shared session storage when running several instances.
- **An external identity provider (Keycloak, Auth0, Cognito):** the right choice for larger products, but adds infrastructure and cost before there is a need.
- **Accounts for clients too:** rejected for now because of booking friction (see the customer research).
