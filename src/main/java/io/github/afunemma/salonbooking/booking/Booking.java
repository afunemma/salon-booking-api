package io.github.afunemma.salonbooking.booking;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A client's appointment: who, when it starts and how long it takes.
 */
public record Booking(String clientName, LocalTime start, Duration duration) {

	public Booking {
		Objects.requireNonNull(clientName, "clientName must not be null");
		Objects.requireNonNull(start, "start must not be null");
		Objects.requireNonNull(duration, "duration must not be null");
		if (duration.isNegative() || duration.isZero()) {
			throw new IllegalArgumentException("duration must be positive");
		}
	}

	public LocalTime end() {
		return start.plus(duration);
	}

	/**
	 * Two time ranges overlap when each one starts before the other ends.
	 * Touching ranges (one ends at 10:35, the next starts at 10:35) do not overlap.
	 */
	public boolean overlaps(LocalTime otherStart, LocalTime otherEnd) {
		return start.isBefore(otherEnd) && otherStart.isBefore(end());
	}
}
