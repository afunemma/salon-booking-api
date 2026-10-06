package io.github.afunemma.salonbooking.reminder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.github.afunemma.salonbooking.booking.Booking;
import io.github.afunemma.salonbooking.booking.BookingStatus;

public interface BookingReminderRepository extends JpaRepository<BookingReminder, Long> {

	Optional<BookingReminder> findByBookingId(Long bookingId);

	/**
	 * Active bookings on a date whose client hasn't been reminded yet. Bookings whose
	 * last attempt failed are included, so they are retried. The service and salon are
	 * loaded in the same query, because the message needs their names.
	 */
	@Query("""
			select b from Booking b
			join fetch b.service
			join fetch b.salon
			where b.bookingDate = :date
			  and b.status = :active
			  and not exists (
			    select r.id from BookingReminder r where r.bookingId = b.id and r.status = :sent)
			order by b.startTime
			""")
	List<Booking> findBookingsToRemind(LocalDate date, BookingStatus active, ReminderStatus sent);

	default List<Booking> findBookingsToRemind(LocalDate date) {
		return findBookingsToRemind(date, BookingStatus.BOOKED, ReminderStatus.SENT);
	}

}
