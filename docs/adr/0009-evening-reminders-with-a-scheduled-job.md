# ADR-0009: Send evening-before reminders with a scheduled job

- **Status:** Accepted
- **Date:** 2026-10-06

## Context

No-shows are the problem salons say costs them the most. The first interviewed barber loses about 3 clients a week (roughly 10%). Clients book 1–2 days ahead and give only a phone number. The barber doesn't read messages, so reminders must go out without him doing anything.

Constraints:

- **Budget is zero.** SMS costs about R0.25–0.35 per message in South Africa. WhatsApp's Business API charges for business-initiated messages and needs a verified Meta business account.
- **The database is Neon's free plan,** which sleeps after 5 minutes idle and has limited compute hours. A job that queries every minute would keep it awake around the clock.
- **The app runs as one instance** on Render.

## Decision

- **When:** the evening before the appointment. A Spring `@Scheduled` job runs every hour from **18:00 to 21:00** salon time (`app.reminders.cron`) and reminds every client with an active (`BOOKED`) booking tomorrow who hasn't been reminded yet. Bookings made during the evening are picked up by the next run.
- **Recording:** a `booking_reminder` table with one row per booking, holding status (`SENT` or `FAILED`), attempts and times. `booking_id` is `UNIQUE`, so the database itself allows at most one reminder record per booking.
- **Retries:** a failed send is recorded as `FAILED` and retried on the next run, up to four tries in one evening. One failure doesn't stop the other reminders.
- **Sending outside transactions:** the job loads the due reminders in one short transaction, sends each message with no transaction open, then records the result in another short transaction. A slow messaging provider never holds a database connection.
- **Delivery semantics: at least once.** If the app crashes after sending but before recording, the next run sends that reminder again. A rare duplicate is better than a missed reminder.
- **Channel behind an interface:** the job only knows `ReminderSender`. The current implementation, `LoggingReminderSender`, writes a log line and delivers nothing. A WhatsApp or SMS sender can be added later without changing the job.
- **Privacy (POPIA):** logs show the booking id and the last three digits of the phone number, never the name, full number or message text.
- **The reminder package depends on booking, never the other way round,** so booking code stays unaware of reminders (ADR-0005).

## Consequences

- **Neon stays mostly asleep:** the job touches the database only 4 times a day.
- **Same-evening bookings get a reminder** shortly after booking, which also works as a confirmation.
- **Reminders need the app to be awake at 18:00–21:00.** On Render's free plan the uptime monitor keeps it awake (ADR-0008).
- **One instance only.** With two instances, both could send the same reminder in the same run. Before scaling out, add a scheduler lock (e.g. ShedLock) or claim rows with `SELECT ... FOR UPDATE SKIP LOCKED`.
- **No real messages yet.** Until a paid channel is chosen, reminders are visible only in the logs and in the `salon_reminders_total{result}` metric.
- **A cancellation between loading and sending** (a window of milliseconds) could still be reminded. Acceptable at this scale.

## Alternatives considered

- **Create a reminder row when the booking is made** (an outbox written in the same transaction): exact due times, but more moving parts, and it couples booking to reminders. Scanning tomorrow's bookings needs no change to booking code.
- **Reminders a few hours before:** needs frequent runs to be accurate, which keeps Neon awake.
- **A message queue or a job framework (Quartz, JobRunr):** reliable at scale, but extra infrastructure for 4 runs a day.
- **Paying for SMS now:** possible later. The `ReminderSender` interface keeps that a small change.
