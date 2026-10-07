package io.github.afunemma.salonbooking.booking;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	List<Booking> findBySalonIdAndBookingDateAndStatusOrderByStartTime(Long salonId, LocalDate bookingDate,
			BookingStatus status);

	List<Booking> findBySalonIdAndBookingDateOrderByStartTime(Long salonId, LocalDate bookingDate);

	/**
	 * How many of a salon's bookings in a date range have each status, counted by the
	 * database in one query instead of loading every booking.
	 */
	@Query("""
			select b.status as status, count(b) as count from Booking b
			where b.salon.id = :salonId and b.bookingDate between :from and :to
			group by b.status
			""")
	List<StatusCount> countByStatus(Long salonId, LocalDate from, LocalDate to);

	long countBySalonIdAndStatusAndBookingDateBetween(Long salonId, BookingStatus status, LocalDate from, LocalDate to);

	/** One row of {@link #countByStatus}. */
	interface StatusCount {

		BookingStatus getStatus();

		long getCount();

	}

}
