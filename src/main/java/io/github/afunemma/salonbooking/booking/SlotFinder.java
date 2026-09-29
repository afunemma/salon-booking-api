package io.github.afunemma.salonbooking.booking;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Works out which start times are still free for a service on one day.
 * <p>
 * Deliberately knows nothing about the database: it works on plain time ranges,
 * so it is easy to test and does not change when the persistence layer does.
 */
public final class SlotFinder {

	private SlotFinder() {
	}

	/**
	 * @param hours         when the salon opens and closes
	 * @param taken         times that are already booked
	 * @param serviceLength how long the new appointment will take
	 * @param step          gap between candidate start times, e.g. every 15 minutes
	 * @return every start time where the service fits without clashing, in order
	 */
	public static List<LocalTime> findFreeSlots(OpeningHours hours, List<TimeRange> taken,
			Duration serviceLength, Duration step) {
		Objects.requireNonNull(hours, "hours must not be null");
		Objects.requireNonNull(taken, "taken must not be null");
		Objects.requireNonNull(serviceLength, "serviceLength must not be null");
		Objects.requireNonNull(step, "step must not be null");
		if (serviceLength.toMinutes() < 1) {
			throw new IllegalArgumentException("serviceLength must be at least one minute");
		}
		if (step.toMinutes() < 1) {
			throw new IllegalArgumentException("step must be at least one minute");
		}

		List<LocalTime> freeSlots = new ArrayList<>();
		// Count minutes from opening instead of adding to LocalTime directly,
		// because LocalTime wraps around midnight (23:30 + 1 hour = 00:30).
		long minutesOpen = Duration.between(hours.open(), hours.close()).toMinutes();
		long serviceMinutes = serviceLength.toMinutes();

		for (long offset = 0; offset + serviceMinutes <= minutesOpen; offset += step.toMinutes()) {
			LocalTime start = hours.open().plusMinutes(offset);
			TimeRange candidate = new TimeRange(start, start.plusMinutes(serviceMinutes));
			if (taken.stream().noneMatch(candidate::overlaps)) {
				freeSlots.add(start);
			}
		}
		return freeSlots;
	}
}
