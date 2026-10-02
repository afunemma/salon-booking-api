package io.github.afunemma.salonbooking.scheduling;

import java.time.LocalTime;
import java.util.Objects;

/**
 * When a salon opens and closes on a given day, e.g. 09:00 to 20:00.
 */
public record OpeningHours(LocalTime open, LocalTime close) {

	public OpeningHours {
		Objects.requireNonNull(open, "open must not be null");
		Objects.requireNonNull(close, "close must not be null");
		if (!open.isBefore(close)) {
			throw new IllegalArgumentException("open must be before close");
		}
	}
}
