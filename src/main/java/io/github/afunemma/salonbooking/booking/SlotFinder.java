package io.github.afunemma.salonbooking.booking;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Works out which start times are still free for a service on one day.
 */
public final class SlotFinder {

	private SlotFinder() {
	}

	/**
	 * @param hours    when the salon opens and closes
	 * @param bookings appointments that are already taken
	 * @param service  the service the client wants
	 * @param step     gap between candidate start times, e.g. every 15 minutes
	 * @return every start time where the service fits without clashing, in order
	 */
	public static List<LocalTime> findFreeSlots(OpeningHours hours, List<Booking> bookings,
			ServiceOffering service, Duration step) {
		Objects.requireNonNull(hours, "hours must not be null");
		Objects.requireNonNull(bookings, "bookings must not be null");
		Objects.requireNonNull(service, "service must not be null");
		Objects.requireNonNull(step, "step must not be null");
		if (step.toMinutes() < 1) {
			throw new IllegalArgumentException("step must be at least one minute");
		}

		List<LocalTime> freeSlots = new ArrayList<>();
		// Count minutes from opening instead of adding to LocalTime directly,
		// because LocalTime wraps around midnight (23:30 + 1 hour = 00:30).
		long minutesOpen = Duration.between(hours.open(), hours.close()).toMinutes();
		long serviceMinutes = service.duration().toMinutes();

		for (long offset = 0; offset + serviceMinutes <= minutesOpen; offset += step.toMinutes()) {
			LocalTime start = hours.open().plusMinutes(offset);
			LocalTime end = start.plus(service.duration());
			if (bookings.stream().noneMatch(booking -> booking.overlaps(start, end))) {
				freeSlots.add(start);
			}
		}
		return freeSlots;
	}
}
