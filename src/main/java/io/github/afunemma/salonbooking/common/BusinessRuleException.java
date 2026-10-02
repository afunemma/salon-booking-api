package io.github.afunemma.salonbooking.common;

/**
 * The request is well-formed but breaks a business rule, e.g. booking a date in the past.
 * Mapped to HTTP 400.
 */
public abstract class BusinessRuleException extends RuntimeException {

	private final String title;

	protected BusinessRuleException(String title, String message) {
		super(message);
		this.title = title;
	}

	/** Short, human-readable summary, e.g. "Booking not allowed". */
	public String getTitle() {
		return title;
	}

}
