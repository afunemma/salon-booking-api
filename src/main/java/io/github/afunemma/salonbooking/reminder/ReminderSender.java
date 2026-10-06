package io.github.afunemma.salonbooking.reminder;

/**
 * Delivers a reminder to a client's phone.
 * <p>
 * The reminder job only knows this interface, so a real channel (WhatsApp or SMS) can be
 * added as another implementation without changing the job. Today the only implementation
 * is {@link LoggingReminderSender}.
 */
public interface ReminderSender {

	/**
	 * Sends the message, or throws an exception if it couldn't be delivered. A failed
	 * reminder is retried on the job's next run.
	 */
	void send(ReminderMessage message);

}
