# ADR-0015: Send reminders from one platform WhatsApp number for now, not each salon's own

- **Status:** Accepted
- **Date:** 2026-10-14

## Context

WhatsApp reminders (ADR-0014) are sent from one number that belongs to the platform. Salon owners may prefer reminders to come from their own salon's name and number, which clients already know.

Facts checked on 14 October 2026:

- **The normal WhatsApp app can't be automated.** Only the WhatsApp Business Platform (Cloud API) may send messages from software. Unofficial tools that control WhatsApp Web break WhatsApp's terms, and numbers using them get banned.
- **Each salon can connect its own number through Meta's "Embedded Signup".** The owner logs in with Facebook inside our app and links their number. Messages then come from the salon, and Meta bills the salon's own WhatsApp account. To offer this, the platform must become a Meta **Tech Provider**: a verified Meta business plus app review. Providers estimate 3–8 weeks of integration work. ([Twilio](https://twilio.com/docs/whatsapp/tech-provider-program), [Telnyx](https://developers.telnyx.com/docs/messaging/whatsapp/embedded-signup/tech-provider))
- **"Coexistence" lets one number be used in the WhatsApp Business app and the API at the same time.** For South African (+27) numbers the sources disagree: one says supported since April 2026, another says not supported, so a salon would need a second number. ([Chakra: support by country](https://chakrahq.com/product/whatsapp/tools/whatsapp-coexistence-support/), [Chakra: 2026 update](https://chakrahq.com/article/whatsapp-coexistence-live-eu-uk-europe-whatsapp-business-for-api-live/))
- **The interviewed barber doesn't read messages.** If reminders came from his number, clients' replies ("can I come at 11?") would land in a WhatsApp he doesn't read. The cancel link (ADR-0010) lets clients act without replying.

## Decision

- **Keep one platform number** for all salons, as built in ADR-0014.
- **Connect each salon's own number later,** through Embedded Signup, once several real salons use the app and ask for it. Recorded on the roadmap.
- **Never automate the WhatsApp app** (WhatsApp Web tools or similar), whatever the convenience.
- **Don't build "click to send" links (`wa.me`) now:** they're free and use the owner's number, but someone must tap each one every evening, which the barber won't do.

## Consequences

- **Clients see the platform's name,** not the salon's. The message text names the salon, so clients still know who it's from.
- **The platform pays for all reminders** until salons connect their own numbers. At about R0.20 per reminder, a pricing plan would have to cover it.
- **No Tech Provider work yet.** That avoids weeks of Meta onboarding before any salon has asked for it.
- **When salons do connect their own numbers,** the change is contained: `WhatsAppReminderSender` would look up each salon's phone number id and token, which must be stored encrypted, instead of reading one from configuration. The reminder job and `ReminderSender` interface stay the same.

## Alternatives considered

- **Each salon's own number now (Embedded Signup):** the best experience for larger salons, but weeks of Meta onboarding and per-salon secrets to store, before there is a single paying salon.
- **Coexistence on the owner's existing number:** unclear availability for +27 numbers, and replies would reach owners who may not read them.
- **Unofficial WhatsApp automation:** breaks WhatsApp's terms; numbers get banned.
- **`wa.me` click-to-send links:** free, but manual every evening.
