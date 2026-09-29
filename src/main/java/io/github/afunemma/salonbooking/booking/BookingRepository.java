package io.github.afunemma.salonbooking.booking;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

	List<Booking> findBySalonIdAndBookingDateAndStatusOrderByStartTime(Long salonId, LocalDate bookingDate,
			BookingStatus status);
}
