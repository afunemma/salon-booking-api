# ADR-0007: Rate-limit login attempts in memory

- **Status:** Accepted
- **Date:** 2026-10-02

## Context

Without a limit, an attacker can try passwords as fast as the server answers. ADR-0006 listed this as a known gap.

Locking an account after a few failures has its own risk: anyone who knows an owner's email could lock them out on purpose.

## Decision

Two token-bucket limits (Bucket4j), checked before any password is verified:

| Limit | Default | Stops |
|---|---|---|
| All login attempts **per IP address** | 20 per minute | One machine trying many accounts |
| **Failed** attempts **per email** | 5 per 15 minutes | Many guesses at one account's password |

- Only *failures* count against an email, and a successful login resets the count. A lockout attack would have to keep failing on purpose, and it only ever *slows* the owner down; it never locks them out permanently.
- Blocked attempts get `429 Too Many Requests` with a `Retry-After` header and are counted in the `salon_auth_logins_total{result="rate_limited"}` metric.
- Buckets are kept in memory, in a Caffeine cache that expires idle entries and holds at most 100,000 keys, so memory stays bounded even under attack.
- The limits are configurable in `app.security.*`.

## Consequences

- Password guessing becomes very slow: about 20 guesses per 15 minutes per account at most, instead of thousands per minute.
- **Each app instance keeps its own counts.** With N instances an attacker gets up to N times the limit. A shared store (e.g. Redis) would be needed for exact limits across instances. That isn't needed while the app runs as one instance.
- **Counts reset when the app restarts.**
- **The per-IP limit needs the real client IP.** Behind a proxy or load balancer, the app must trust the `X-Forwarded-For` header (`server.forward-headers-strategy`). Otherwise every user shares the proxy's IP and its limit. This must be configured when deploying.

## Alternatives considered

- **Locking the account after N failures:** simple, but lets anyone lock out a known owner.
- **A shared store (Redis):** exact limits across instances, but extra infrastructure before it's needed.
- **Rate limiting at a gateway or CDN** (e.g. Cloudflare): good to add in front, but the app shouldn't rely on it alone.
- **CAPTCHA after failures:** a good later addition for a web frontend. It doesn't fit an API-only project.
