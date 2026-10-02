package io.github.afunemma.salonbooking.booking;

import io.github.afunemma.salonbooking.common.ConflictException;

/**
 * Thrown when a booking can't move to the requested status, e.g. cancelling one that is
 * already completed.
 */
public class InvalidBookingStateException extends ConflictException {

	public InvalidBookingStateException(String message) {
		super("Invalid booking state", message);
	}

}
