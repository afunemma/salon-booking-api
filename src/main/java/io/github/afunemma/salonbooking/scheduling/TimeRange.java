package io.github.afunemma.salonbooking.scheduling;

import java.time.LocalTime;
import java.util.Objects;

/**
 * A stretch of time on one day, e.g. 10:00 to 10:35.
 */
public record TimeRange(LocalTime start, LocalTime end) {

	public TimeRange {
		Objects.requireNonNull(start, "start must not be null");
		Objects.requireNonNull(end, "end must not be null");
		if (!start.isBefore(end)) {
			throw new IllegalArgumentException("start must be before end");
		}
	}

	/**
	 * Two ranges overlap when each one starts before the other ends. Touching ranges (one
	 * ends at 10:35, the next starts at 10:35) do not overlap.
	 */
	public boolean overlaps(TimeRange other) {
		return start.isBefore(other.end) && other.start.isBefore(end);
	}
}
