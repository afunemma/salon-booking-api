package io.github.afunemma.salonbooking.reminder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * A reminder ready to deliver, with each detail kept separate: a WhatsApp template needs
 * them as separate parameters, while a log or SMS uses the whole {@link #text()}.
 *
 * @param bookingId the booking it is about
 * @param phone the client's phone number as they typed it, e.g. 082 123 4567
 */
public record ReminderMessage(Long bookingId, String phone, String clientName, String serviceName, String salonName,
		LocalDate date, LocalTime startTime, String cancelUrl) {

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH);

	/**
	 * The values for the template's placeholders {{1}} to {{6}}, in order. The template
	 * text itself is approved by Meta and stored there (see ADR-0014).
	 */
	public List<String> templateParameters() {
		return List.of(clientName, serviceName, salonName, DAY.format(date), startTime.toString(), cancelUrl);
	}

	/** The whole message as the client reads it. */
	public String text() {
		return "Hi %s, a reminder of your %s at %s tomorrow, %s, at %s. Can't make it? Cancel here so someone else can have the slot: %s"
			.formatted(templateParameters().toArray());
	}

}
