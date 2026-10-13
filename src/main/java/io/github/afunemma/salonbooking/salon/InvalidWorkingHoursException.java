package io.github.afunemma.salonbooking.salon;

import io.github.afunemma.salonbooking.common.BusinessRuleException;

/**
 * Thrown when a staff member's working hours fall outside the salon's opening hours.
 */
public class InvalidWorkingHoursException extends BusinessRuleException {

	public InvalidWorkingHoursException(String message) {
		super("Invalid working hours", message);
	}

}
