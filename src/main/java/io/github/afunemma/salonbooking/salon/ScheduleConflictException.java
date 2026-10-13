package io.github.afunemma.salonbooking.salon;

import io.github.afunemma.salonbooking.common.ConflictException;

/**
 * Thrown when a schedule change would leave existing bookings on a day off, outside
 * working hours or during a break.
 */
public class ScheduleConflictException extends ConflictException {

	public ScheduleConflictException(String message) {
		super("Schedule conflicts with bookings", message);
	}

}
