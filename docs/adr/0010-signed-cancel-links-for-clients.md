# ADR-0010: Let clients cancel from a signed link

- **Status:** Accepted
- **Date:** 2026-10-07

## Context

Reminders (ADR-0009) tell clients about their appointment, but a client who can't come still had to contact the salon. The interviewed barber doesn't read messages, so in practice that client becomes a no-show and the slot is lost.

Clients book without an account (ADR-0006), so they have no login to prove a booking is theirs. Reminders go to phones by SMS or WhatsApp, so the link must be short, and it opens in a phone browser.

## Decision

- **A signed link per booking:** `/bookings/{id}/cancel?token=…`. The token is HMAC-SHA256 of `cancel-booking:{id}` with a server-side secret, cut to 128 bits (22 URL-safe characters). Changing the id breaks the signature, and guessing a token is not feasible.
- **Nothing stored:** the token is recomputed when it's needed or checked. No database column, and no migration.
- **Single use comes from the booking itself:** once the booking is cancelled, completed, marked as a no-show or has started, the page no longer offers to cancel.
- **Opening the link changes nothing (GET is safe).** WhatsApp and other chat apps open links in the background to build previews, so a GET that cancelled would cancel bookings by accident. The page shows the appointment and a **Cancel my booking** button, which sends a POST and redirects back to the page (Post/Redirect/Get).
- **Its own key:** `APP_SECURITY_CANCEL_LINK_SECRET`, separate from the login-token key, so each key has one job and can be rotated on its own.
- **Server-rendered HTML with Thymeleaf,** outside `/api/v1`, because clients open it in a browser, not an API tool. Thymeleaf escapes every value, so a name like `<script>` is shown as text.
- **Wrong token and unknown booking look the same** (404 "link not valid"), so links can't be used to find out which bookings exist.
- **Tokens are compared in constant time** (`MessageDigest.isEqual`).
- **The client who booked gets the link** in the booking response (`cancelUrl`) as well as in the reminder. The owner's day view leaves it out.
- **The page shows only** the client's name, the service, the salon and the time, never the phone number. It asks browsers not to send the link to other sites (`no-referrer`) and search engines not to index it (`noindex`).

## Consequences

- **A cancelled slot is free again straight away,** so another client can book it.
- **Anyone holding the link can cancel that one booking:** for example, if the client forwards the message. This is the same trust model as a "manage booking" link in an airline email, and acceptable for a haircut.
- **Links can't be revoked one by one.** Changing the secret invalidates all links at once. This is acceptable because a link only works until the appointment starts.
- **Without a configured secret,** a random key is used and links break when the app restarts. Production must set `APP_SECURITY_CANCEL_LINK_SECRET`.
- **The token appears in the URL,** so it can end up in browser history or a proxy's access logs. It only allows cancelling one booking, and it stops working once the appointment starts.

## Alternatives considered

- **A random token stored on the booking** (hashed, like a password reset token): can be revoked per booking, but needs a column and a migration. The reminder job would also need the raw token later, and it can't get that back from a hash.
- **A JWT in the link:** works, but is much longer (too long for an SMS) and adds an expiry the booking date already gives.
- **Cancelling directly on GET:** one tap fewer, but link previews would cancel bookings.
- **A client account or a one-time code by SMS:** stronger, but far too much friction for one haircut.
