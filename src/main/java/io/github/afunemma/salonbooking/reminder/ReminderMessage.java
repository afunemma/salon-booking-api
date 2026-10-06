package io.github.afunemma.salonbooking.reminder;

/**
 * A reminder ready to deliver.
 *
 * @param bookingId the booking it is about
 * @param phone the client's phone number
 * @param text the message the client receives
 */
public record ReminderMessage(Long bookingId, String phone, String text) {
}
