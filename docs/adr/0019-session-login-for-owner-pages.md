# ADR-0019: Session login with CSRF protection for the owner pages

- **Status:** Accepted
- **Date:** 2026-10-19

## Context

Owners managed their salon through the JSON API only, with a JWT in the `Authorization` header (ADR-0006). The barber needs a simple web page for his day: see the bookings, mark them done or a no-show.

A plain HTML page can't send an `Authorization` header. The usual workaround is JavaScript that keeps the JWT in `localStorage`, where any injected script can read and steal it.

## Decision

- **A second Spring Security filter chain for `/owner/**`,** checked first (`@Order(1)`). The API keeps its own chain (`@Order(2)`), unchanged: stateless, JWT, CSRF off.
- **Owner pages use a login form and a session cookie:**
  - HttpOnly (the default), so scripts can't read it
  - `SameSite=Lax`, so other sites' forms don't send it
  - `Secure` in production, so it only travels over HTTPS
  - an 8-hour session, about a working day
- **CSRF protection is on** for the owner pages. Because the browser sends the cookie automatically, another website could otherwise make a logged-in owner's browser cancel bookings. Thymeleaf adds the CSRF token to every form; a form without it gets `403`.
- **One login rule for both:** the login form goes through the same `AuthService.authenticate` as the API, with the same rate limits (ADR-0007) and the same answer for an unknown email and a wrong password. The session keeps only the user id and email, never the password.
- **The same services** as the API, so ownership checks and booking rules apply once. Another owner's salon gives the same "not found" page as one that doesn't exist.
- **A separate `owner` package:** it depends on `account`, `salon` and `booking`, and nothing depends on it, so there is no package cycle (ADR-0005).

## Consequences

- The barber can run his day from his phone without Swagger UI.
- **Sessions live in the app's memory:** a restart or deploy logs owners out, and several instances would need a shared session store (e.g. Spring Session with Redis or PostgreSQL).
- **Two security set-ups to understand.** Each one is simple, and tests prove they stay separate: the API sets no session cookie, and an API token doesn't open the owner pages.
- **This first version covers the daily work:** register, log in, create a salon, and the day view. Services, prices, staff and time off still go through the API, as the next step.

## Alternatives considered

- **A JavaScript front end keeping the JWT in `localStorage`:** one security set-up, but the token is open to any script injected into the page.
- **The JWT in an HttpOnly cookie:** safe from scripts, but needs custom token reading and CSRF protection anyway, i.e. a session login written by hand.
- **One shared chain with sessions for everything:** the API would then need CSRF tokens too, which API clients don't expect.
