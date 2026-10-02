package io.github.afunemma.salonbooking.booking;

/**
 * Thrown when a booking request breaks a business rule, e.g. a date in the past.
 * Mapped to HTTP 400.
 */
public class BookingNotAllowedException extends RuntimeException {

	public BookingNotAllowedException(String message) {
		super(message);
	}
}
