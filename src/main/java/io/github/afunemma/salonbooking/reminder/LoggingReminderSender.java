package io.github.afunemma.salonbooking.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

/**
 * Stand-in sender that only writes a log line; no message reaches the client. Used unless
 * WhatsApp is switched on ({@link WhatsAppReminderSender}), which costs money per message
 * and needs a Meta account. The client's name, phone number and the message text are
 * personal information (POPIA), so only the last three digits of the phone number are
 * logged.
 */
@Component
@ConditionalOnBooleanProperty(name = "app.reminders.whatsapp.enabled", havingValue = false, matchIfMissing = true)
class LoggingReminderSender implements ReminderSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingReminderSender.class);

	@Override
	public void send(ReminderMessage message) {
		log.info("Reminder for booking {} to phone ending {} (logged only, not delivered)", message.bookingId(),
				lastDigits(message.phone()));
	}

	/** The last three digits, e.g. "567" for "082 123 4567". */
	static String lastDigits(String phone) {
		String digits = phone.replaceAll("[^0-9]", "");
		return digits.substring(Math.max(0, digits.length() - 3));
	}

}
