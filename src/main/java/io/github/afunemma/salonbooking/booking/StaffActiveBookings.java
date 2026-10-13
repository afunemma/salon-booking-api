package io.github.afunemma.salonbooking.booking;

import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.github.afunemma.salonbooking.salon.ActiveBookings;

/**
 * The booking package's side of {@link ActiveBookings}: lets the salon package check a
 * schedule change or new time off against bookings without depending on this package.
 */
@Component
@Transactional(readOnly = true)
class StaffActiveBookings implements ActiveBookings {

	private final BookingRepository bookings;

	StaffActiveBookings(BookingRepository bookings) {
		this.bookings = bookings;
	}

	@Override
	public List<BookedTime> forStaffFrom(Long staffId, LocalDate from) {
		return bookings
			.findByStaffIdAndStatusAndBookingDateGreaterThanEqualOrderByBookingDateAscStartTimeAsc(staffId,
					BookingStatus.BOOKED, from)
			.stream()
			.map(booking -> new BookedTime(booking.getBookingDate(), booking.getTimeRange()))
			.toList();
	}

	@Override
	public List<BookedTime> inPeriod(Long salonId, @Nullable Long staffId, LocalDate first, LocalDate last) {
		List<Booking> found = staffId != null
				? bookings.findByStaffIdAndStatusAndBookingDateBetweenOrderByBookingDateAscStartTimeAsc(staffId,
						BookingStatus.BOOKED, first, last)
				: bookings.findBySalonIdAndStatusAndBookingDateBetweenOrderByBookingDateAscStartTimeAsc(salonId,
						BookingStatus.BOOKED, first, last);
		return found.stream().map(booking -> new BookedTime(booking.getBookingDate(), booking.getTimeRange())).toList();
	}

}
