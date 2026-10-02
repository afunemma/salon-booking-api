package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.afunemma.salonbooking.FixedClockConfiguration;
import io.github.afunemma.salonbooking.booking.BookingDtos.CreateBookingRequest;
import io.github.afunemma.salonbooking.common.AppProperties;
import io.github.afunemma.salonbooking.salon.Salon;
import io.github.afunemma.salonbooking.salon.SalonService;
import io.github.afunemma.salonbooking.salon.ServiceOffering;
import io.github.afunemma.salonbooking.scheduling.OpeningHours;

/**
 * Unit tests for the database-error handling in {@link BookingService#book}.
 * <p>
 * In normal use the salon lock stops overlaps before they reach the database, so this
 * path is simulated: the repository is mocked to fail the way PostgreSQL does.
 */
class BookingServiceTest {

	private static final LocalDate TOMORROW = LocalDate.of(2030, 1, 7);

	private final BookingRepository bookings = mock(BookingRepository.class);

	private final SalonService salonService = mock(SalonService.class);

	private BookingService bookingService;

	private CreateBookingRequest request;

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(FixedClockConfiguration.NOW.toInstant(), FixedClockConfiguration.NOW.getZone());
		AppProperties properties = new AppProperties(ZoneId.of("Africa/Johannesburg"),
				new AppProperties.Booking(Duration.ofMinutes(15)));
		bookingService = new BookingService(bookings, salonService, clock, properties);

		Salon salon = new Salon("Sipho's Cuts", new OpeningHours(LocalTime.of(9, 0), LocalTime.of(20, 0)));
		ReflectionTestUtils.setField(salon, "id", 1L);
		ServiceOffering haircut = new ServiceOffering(salon, "Haircut", Duration.ofMinutes(35), null, null);
		ReflectionTestUtils.setField(haircut, "id", 2L);

		when(salonService.findService(1L, 2L)).thenReturn(haircut);
		when(bookings.findBySalonIdAndBookingDateAndStatusOrderByStartTime(1L, TOMORROW, BookingStatus.BOOKED))
			.thenReturn(List.of());
		request = new CreateBookingRequest(2L, "Thabo", "082 123 4567", TOMORROW, LocalTime.of(10, 0));
	}

	@Test
	@DisplayName("An overlap rejected by the database becomes 'slot unavailable'")
	void overlapConstraintBecomesSlotUnavailable() {
		when(bookings.saveAndFlush(any())).thenThrow(databaseError("23P01", "booking_no_overlap"));

		assertThatThrownBy(() -> bookingService.book(1L, request)).isInstanceOf(SlotUnavailableException.class)
			.hasMessageContaining("just booked by someone else");
	}

	@Test
	@DisplayName("Any other database error is not disguised as 'slot unavailable'")
	void otherConstraintIsRethrown() {
		when(bookings.saveAndFlush(any())).thenThrow(databaseError("23514", "booking_starts_before_ends"));

		assertThatThrownBy(() -> bookingService.book(1L, request)).isInstanceOf(DataIntegrityViolationException.class);
	}

	/** Builds the exception chain Spring produces when PostgreSQL rejects an insert. */
	private static DataIntegrityViolationException databaseError(String sqlState, String constraint) {
		// PostgreSQL error fields: S = severity, C = SQL state, n = constraint name, M =
		// message.
		ServerErrorMessage error = new ServerErrorMessage(
				"SERROR\0C" + sqlState + "\0n" + constraint + "\0Mconstraint violated\0");
		return new DataIntegrityViolationException("could not execute statement", new PSQLException(error));
	}

}
