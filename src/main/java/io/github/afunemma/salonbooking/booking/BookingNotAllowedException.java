package io.github.afunemma.salonbooking.booking;

import io.github.afunemma.salonbooking.common.BusinessRuleException;

/**
 * Thrown when a booking request breaks a business rule, e.g. a date in the past.
 */
public class BookingNotAllowedException extends BusinessRuleException {

	public BookingNotAllowedException(String message) {
		super("Booking not allowed", message);
	}

}
