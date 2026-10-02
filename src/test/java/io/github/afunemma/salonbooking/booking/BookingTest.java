package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

import io.github.afunemma.salonbooking.salon.Salon;
import io.github.afunemma.salonbooking.salon.ServiceOffering;

class BookingTest {

	private final Salon salon = new Salon("Sipho's Cuts", new OpeningHours(LocalTime.of(9, 0), LocalTime.of(20, 0)));
	private final ServiceOffering haircut = new ServiceOffering(salon, "Haircut", Duration.ofMinutes(35), null, null);

	@Test
	void newBookingIsBookedAndEndsAfterServiceDuration() {
		Booking booking = new Booking(haircut, "Thabo", "0821234567", LocalDate.of(2026, 10, 5), LocalTime.of(10, 0));

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.BOOKED);
		assertThat(booking.getTimeRange()).isEqualTo(new TimeRange(LocalTime.of(10, 0), LocalTime.of(10, 35)));
	}

	@Test
	void finishedBookingCannotChangeAgain() {
		Booking booking = new Booking(haircut, "Thabo", "0821234567", LocalDate.of(2026, 10, 5), LocalTime.of(10, 0));
		booking.markNoShow();

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.NO_SHOW);
		assertThatThrownBy(booking::cancel).isInstanceOf(InvalidBookingStateException.class);
	}
}
