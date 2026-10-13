# ADR-0014: Deliver reminders by WhatsApp through Meta's Cloud API, switched off by default

- **Status:** Accepted
- **Date:** 2026-10-13

## Context

Reminders (ADR-0009) are only logged so far. Clients in South Africa read WhatsApp far more than SMS or email, and they give only a phone number when booking.

Facts checked on 13 October 2026:

- **Templates are required.** A business may only start a WhatsApp conversation with a message template that Meta has approved. The template's text is stored at Meta; the sender fills in its placeholders.
- **Price:** a utility template to a South African number costs US$0.0095 since 1 October 2026, plus 15% VAT: about R0.20 per reminder, or about R30 a month for a barber with 5 clients a day. ([ChatMaxima](https://chatmaxima.com/whatsapp-api-pricing/south-africa/))
- **Testing:** Meta gives every new developer app a test number that can message up to 5 verified recipients. ([Meta: get started](https://developers.facebook.com/docs/whatsapp/cloud-api/get-started/))
- **The project's budget is still zero,** and the live demo has no Meta account connected.

## Decision

- **`WhatsAppReminderSender`** implements `ReminderSender` (ADR-0009) and sends a template message to `POST {api-url}/{phone-number-id}/messages`. The reminder job didn't change.
- **Switched off by default** (`app.reminders.whatsapp.enabled=false`): `LoggingReminderSender` stays active. Exactly one sender exists at a time, chosen with `@ConditionalOnBooleanProperty`.
- **Validated settings:** switching it on without a phone number id and an access token stops the app at startup, instead of failing at 18:00.
- **The access token comes from the environment** (`APP_REMINDERS_WHATSAPP_ACCESS_TOKEN`) and is never logged.
- **Separate details, not one string:** `ReminderMessage` keeps name, service, salon, day, time and cancel link separate, because the template needs them as six parameters. The full text is still available for logs.
- **International numbers:** clients type `082 123 4567`; WhatsApp needs `27821234567`. Local numbers get the configured country code (27).
- **Timeouts** (`spring.http.clients.connect-timeout=5s`, `read-timeout=10s`): reminders are sent one after another, so a hanging API must fail fast. A failed or timed-out send is retried on the next run (ADR-0009).
- **Privacy:** Meta's error responses can repeat the phone number, so only the HTTP status is logged.

### The template to submit to Meta

Name `appointment_reminder`, category **Utility**, language **English (en)**:

> Hi {{1}}, a reminder of your {{2}} at {{3}} tomorrow, {{4}}, at {{5}}. Can't make it? Cancel here so someone else can have the slot: {{6}}

With example values: Thabo · Haircut · Sipho's Cuts · Monday 7 January · 10:00 · https://salon-booking-api-wguy.onrender.com/bookings/42/cancel?token=example

## Consequences

- **Turning it on is a configuration change, not a code change:** set `APP_REMINDERS_WHATSAPP_ENABLED=true`, `APP_REMINDERS_WHATSAPP_PHONE_NUMBER_ID` and `APP_REMINDERS_WHATSAPP_ACCESS_TOKEN`.
- **It costs money per reminder once live,** and needs a Meta Business account, an approved template and, for real clients, a verified business number.
- **Tests never contact Meta:** the request is checked against a fake server (`MockRestServiceServer`), and the timeout against a local server that answers too slowly.
- **Delivery isn't confirmed.** Meta accepting the request doesn't mean the client received it. Delivery status arrives through webhooks, which aren't handled yet.
- **Changing the template's wording happens at Meta,** not in this code. The order of the six parameters must stay the same.

## Alternatives considered

- **SMS** (BulkSMS, Twilio): about R0.25–0.35 per message, lower open rates, and clients are used to WhatsApp.
- **A WhatsApp provider such as Twilio or 360dialog:** easier onboarding, but adds a markup on Meta's price and another account.
- **Free-form WhatsApp messages:** not allowed for business-initiated messages.
- **A hardcoded switch in code, or a separate build:** a property keeps one build for every environment.
