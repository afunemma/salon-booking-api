package io.github.afunemma.salonbooking.salon;

import io.github.afunemma.salonbooking.common.BusinessRuleException;

/**
 * Thrown when time off is backwards, already over, or longer than allowed.
 */
public class InvalidTimeOffException extends BusinessRuleException {

	public InvalidTimeOffException(String message) {
		super("Invalid time off", message);
	}

}
