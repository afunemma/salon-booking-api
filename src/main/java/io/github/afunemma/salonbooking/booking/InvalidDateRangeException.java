package io.github.afunemma.salonbooking.booking;

import io.github.afunemma.salonbooking.common.BusinessRuleException;

/**
 * Thrown when a requested date range is backwards or too long.
 */
public class InvalidDateRangeException extends BusinessRuleException {

	public InvalidDateRangeException(String message) {
		super("Invalid date range", message);
	}

}
