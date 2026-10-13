package io.github.afunemma.salonbooking.salon;

import java.time.LocalDate;
import java.util.List;

import io.github.afunemma.salonbooking.scheduling.TimeRange;

/**
 * A staff member's active bookings, as the salon package needs them to check a schedule
 * change.
 * <p>
 * The booking package depends on the salon package, so salon code can't use booking
 * classes without creating a package cycle. Instead, salon defines this small interface
 * and the booking package implements it: the dependency still points one way (dependency
 * inversion).
 */
public interface ActiveBookings {

	/** Bookings still marked as booked, from a date on, in date and time order. */
	List<BookedTime> forStaffFrom(Long staffId, LocalDate from);

	record BookedTime(LocalDate date, TimeRange time) {
	}

}
