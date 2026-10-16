package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.afunemma.salonbooking.salon.Salon;
import io.github.afunemma.salonbooking.salon.ServiceOffering;
import io.github.afunemma.salonbooking.salon.StaffMember;
import io.github.afunemma.salonbooking.scheduling.OpeningHours;
import io.github.afunemma.salonbooking.scheduling.TimeRange;

class BookingTest {

	private final Salon salon = new Salon("Sipho's Cuts", new OpeningHours(LocalTime.of(9, 0), LocalTime.of(20, 0)),
			1L);

	private final ServiceOffering haircut = new ServiceOffering(salon, "Haircut", Duration.ofMinutes(35), null, null);

	private final StaffMember sipho = new StaffMember(salon, "Sipho", salon.getOpeningHours());

	@Test
	void newBookingIsBookedAndEndsAfterServiceDuration() {
		Booking booking = new Booking(haircut, sipho, "Thabo", "0821234567", LocalDate.of(2026, 10, 5),
				LocalTime.of(10, 0));

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.BOOKED);
		assertThat(booking.getTimeRange()).isEqualTo(new TimeRange(LocalTime.of(10, 0), LocalTime.of(10, 35)));
	}

	@Test
	void finishedBookingCannotChangeAgain() {
		Booking booking = new Booking(haircut, sipho, "Thabo", "0821234567", LocalDate.of(2026, 10, 5),
				LocalTime.of(10, 0));
		booking.markNoShow();

		assertThat(booking.getStatus()).isEqualTo(BookingStatus.NO_SHOW);
		assertThatThrownBy(booking::cancel).isInstanceOf(InvalidBookingStateException.class);
	}

	@Test
	void staffMemberMustWorkAtTheServicesSalon() {
		Salon other = new Salon("Other Cuts", salon.getOpeningHours(), 2L);
		ReflectionTestUtils.setField(salon, "id", 1L);
		ReflectionTestUtils.setField(other, "id", 2L);
		StaffMember elsewhere = new StaffMember(other, "Lindiwe", other.getOpeningHours());

		assertThatThrownBy(() -> new Booking(haircut, elsewhere, "Thabo", "0821234567", LocalDate.of(2026, 10, 5),
				LocalTime.of(10, 0)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void phoneKeyIgnoresHowTheNumberIsWritten() {
		assertThat(Booking.phoneKey("082 123 4567")).isEqualTo("821234567");
		assertThat(Booking.phoneKey("0821234567")).isEqualTo("821234567");
		assertThat(Booking.phoneKey("+27 82 123 4567")).isEqualTo("821234567");
		assertThat(Booking.phoneKey("+27821234567")).isEqualTo("821234567");
		assertThat(Booking.phoneKey("1234567")).as("shorter than 9 digits").isEqualTo("1234567");
	}

}
