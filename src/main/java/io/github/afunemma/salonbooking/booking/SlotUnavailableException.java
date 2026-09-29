package io.github.afunemma.salonbooking.booking;

/**
 * Thrown when a client tries to book a time that is not free. Mapped to HTTP 409.
 */
public class SlotUnavailableException extends RuntimeException {

	public SlotUnavailableException(String message) {
		super(message);
	}
}
