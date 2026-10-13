package io.github.afunemma.salonbooking.reminder;

/**
 * Delivers a reminder to a client's phone.
 * <p>
 * The reminder job only knows this interface, so the channel can change without changing
 * the job. {@link LoggingReminderSender} (the default) only logs;
 * {@link WhatsAppReminderSender} sends real WhatsApp messages when switched on.
 */
public interface ReminderSender {

	/**
	 * Sends the message, or throws an exception if it couldn't be delivered. A failed
	 * reminder is retried on the job's next run.
	 */
	void send(ReminderMessage message);

}
