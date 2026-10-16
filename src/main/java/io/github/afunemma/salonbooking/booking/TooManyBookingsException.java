package io.github.afunemma.salonbooking.booking;

import io.github.afunemma.salonbooking.common.ConflictException;

/**
 * Thrown when a phone number already has the maximum number of upcoming bookings at a
 * salon, which stops one person filling a salon's diary.
 */
public class TooManyBookingsException extends ConflictException {

	public TooManyBookingsException(String message) {
		super("Too many bookings", message);
	}

}
