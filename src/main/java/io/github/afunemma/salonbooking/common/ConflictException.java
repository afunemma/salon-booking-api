package io.github.afunemma.salonbooking.common;

/**
 * The request clashes with the current state, e.g. the slot was just taken or the booking
 * is already cancelled. Mapped to HTTP 409.
 * <p>
 * Features extend this with their own specific exceptions, so the error handler only
 * needs to know about these general categories, not about every feature.
 */
public abstract class ConflictException extends RuntimeException {

	private final String title;

	protected ConflictException(String title, String message) {
		super(message);
		this.title = title;
	}

	/** Short, human-readable summary, e.g. "Slot unavailable". */
	public String getTitle() {
		return title;
	}

}
