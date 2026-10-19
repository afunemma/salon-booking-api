package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * If the salon changes a booking at the same moment the client presses cancel, the client
 * is simply shown their booking page again, which shows its current state.
 */
class ClientCancelConflictTest {

	@Test
	@DisplayName("A cancel that clashes with a salon change reloads the booking page instead of failing")
	void conflictReloadsThePage() {
		BookingService bookingService = mock(BookingService.class);
		when(bookingService.cancelByClient(42L, "token"))
			.thenThrow(new ObjectOptimisticLockingFailureException(Booking.class, 42L));
		MockMvcTester mvc = MockMvcTester.of(new ClientCancelController(bookingService));

		assertThat(mvc.post().uri("/bookings/42/cancel").param("token", "token")).hasStatus3xxRedirection()
			.hasRedirectedUrl("/bookings/42/cancel?token=token");
	}

}
