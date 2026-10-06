package io.github.afunemma.salonbooking.reminder;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.afunemma.salonbooking.booking.Booking;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Reminds clients the evening before their appointment.
 * <p>
 * Runs every hour from 18:00 to 21:00 (salon time, see {@code app.reminders.cron}). Each
 * run sends a reminder for every active booking tomorrow that hasn't had one yet:
 * <ul>
 * <li>A booking made after 18:00 for tomorrow is picked up by the next run.</li>
 * <li>A failed send is retried on the next run, up to four tries in one evening.</li>
 * <li>Running only four times a day lets a serverless database (Neon) sleep the rest of
 * the time.</li>
 * </ul>
 * Sending happens <b>outside</b> a database transaction, so a slow messaging provider
 * never holds a database connection. The trade-off: if the app crashes after sending but
 * before recording it, the next run sends that reminder again. A rare duplicate is better
 * than a missed reminder, because missed appointments are what salons lose money on.
 */
@Component
class ReminderJob {

	private static final Logger log = LoggerFactory.getLogger(ReminderJob.class);

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);

	private final BookingReminderRepository reminders;

	private final ReminderSender sender;

	private final TransactionTemplate transactions;

	private final Clock clock;

	private final MeterRegistry meters;

	ReminderJob(BookingReminderRepository reminders, ReminderSender sender, TransactionTemplate transactions,
			Clock clock, MeterRegistry meters) {
		this.reminders = reminders;
		this.sender = sender;
		this.transactions = transactions;
		this.clock = clock;
		this.meters = meters;
	}

	@Scheduled(cron = "${app.reminders.cron}", zone = "${app.time-zone}")
	void sendTomorrowsReminders() {
		LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
		List<ReminderMessage> due = Objects.requireNonNull(transactions.execute(
				status -> reminders.findBookingsToRemind(tomorrow).stream().map(ReminderJob::messageFor).toList()));
		int sent = 0;
		for (ReminderMessage message : due) {
			boolean delivered = deliver(message);
			transactions.executeWithoutResult(status -> record(message.bookingId(), delivered));
			meters.counter("salon.reminders", "result", delivered ? "sent" : "failed").increment();
			if (delivered) {
				sent++;
			}
		}
		log.info("Reminders for {}: {} sent, {} failed", tomorrow, sent, due.size() - sent);
	}

	private boolean deliver(ReminderMessage message) {
		try {
			sender.send(message);
			return true;
		}
		catch (RuntimeException ex) {
			// The exception message could contain the phone number, so only its type is
			// logged.
			log.warn("Reminder for booking {} failed: {}; retrying on the next run", message.bookingId(),
					ex.getClass().getSimpleName());
			return false;
		}
	}

	private void record(Long bookingId, boolean delivered) {
		reminders.findByBookingId(bookingId)
			.ifPresentOrElse(reminder -> reminder.recordRetry(clock.instant(), delivered),
					() -> reminders.save(new BookingReminder(bookingId, clock.instant(), delivered)));
	}

	static ReminderMessage messageFor(Booking booking) {
		String text = "Hi %s, a reminder of your %s at %s tomorrow, %s, at %s. If you can't make it, please let the salon know."
			.formatted(booking.getClientName(), booking.getService().getName(), booking.getSalon().getName(),
					DAY.format(booking.getBookingDate()), booking.getStartTime());
		return new ReminderMessage(booking.getId(), booking.getClientPhone(), text);
	}

}
