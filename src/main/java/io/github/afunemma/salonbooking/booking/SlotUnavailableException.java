package io.github.afunemma.salonbooking.booking;

import io.github.afunemma.salonbooking.common.ConflictException;

/**
 * Thrown when a client tries to book a time that is not free.
 */
public class SlotUnavailableException extends ConflictException {

	public SlotUnavailableException(String message) {
		super("Slot unavailable", message);
	}

}
