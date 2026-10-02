package io.github.afunemma.salonbooking.booking;

/**
 * Thrown when a booking can't move to the requested status, e.g. cancelling one
 * that is already completed. Mapped to HTTP 409.
 */
public class InvalidBookingStateException extends RuntimeException {

	public InvalidBookingStateException(String message) {
		super(message);
	}
}
