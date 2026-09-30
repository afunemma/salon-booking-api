package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import io.github.afunemma.salonbooking.FixedClockConfiguration;
import io.github.afunemma.salonbooking.TestcontainersConfiguration;
import io.github.afunemma.salonbooking.booking.BookingDtos.CreateBookingRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.CreateSalonRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.CreateServiceRequest;
import io.github.afunemma.salonbooking.salon.SalonService;

/**
 * Many clients try to book the same slot at the same moment.
 * Exactly one must succeed; everyone else must get "slot unavailable".
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class ConcurrentBookingTest {

	private static final int CLIENTS = 20;
	private static final LocalDate TOMORROW = LocalDate.of(2030, 1, 7);

	@Autowired
	private SalonService salonService;

	@Autowired
	private BookingService bookingService;

	@Autowired
	private BookingRepository bookings;

	@RepeatedTest(3)
	@DisplayName("20 clients booking the same slot at once: exactly one wins")
	void onlyOneOfManySimultaneousBookingsSucceeds() throws Exception {
		long salonId = salonService
				.createSalon(new CreateSalonRequest("Race Cuts", LocalTime.of(9, 0), LocalTime.of(20, 0))).id();
		long haircutId = salonService.addService(salonId, new CreateServiceRequest("Haircut", 35, null, null)).id();

		CountDownLatch startTogether = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(CLIENTS)) {
			for (int i = 0; i < CLIENTS; i++) {
				// Overlapping start times (10:00 and 10:15) must clash too, not just identical ones.
				LocalTime start = i % 2 == 0 ? LocalTime.of(10, 0) : LocalTime.of(10, 15);
				CreateBookingRequest request = new CreateBookingRequest(haircutId, "Client " + i, "0820000000",
						TOMORROW, start);
				results.add(pool.submit(() -> {
					startTogether.await();
					try {
						bookingService.book(salonId, request);
						return true;
					}
					catch (SlotUnavailableException ex) {
						return false;
					}
				}));
			}
			startTogether.countDown();

			int succeeded = 0;
			for (Future<Boolean> result : results) {
				if (result.get()) {
					succeeded++;
				}
			}
			assertThat(succeeded).as("successful bookings").isEqualTo(1);
		}
		assertThat(bookings.findBySalonIdAndBookingDateAndStatusOrderByStartTime(salonId, TOMORROW,
				BookingStatus.BOOKED)).hasSize(1);
	}
}
