package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.afunemma.salonbooking.AuthTestSupport;
import io.github.afunemma.salonbooking.TestcontainersConfiguration;
import io.github.afunemma.salonbooking.account.AppUser;
import io.github.afunemma.salonbooking.account.AppUserRepository;
import io.github.afunemma.salonbooking.salon.Salon;
import io.github.afunemma.salonbooking.salon.SalonRepository;
import io.github.afunemma.salonbooking.salon.ServiceOffering;
import io.github.afunemma.salonbooking.salon.ServiceOfferingRepository;
import io.github.afunemma.salonbooking.salon.StaffMember;
import io.github.afunemma.salonbooking.salon.StaffMemberRepository;
import io.github.afunemma.salonbooking.scheduling.OpeningHours;

/**
 * Two people change the same booking at the same moment: the salon marks it done while
 * the client cancels it. Without optimistic locking, the second save would silently
 * overwrite the first ("lost update"). The latches force the worst order every time.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConcurrentStatusChangeTest {

	@Autowired
	private TransactionTemplate transactions;

	@Autowired
	private BookingRepository bookings;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private SalonRepository salons;

	@Autowired
	private ServiceOfferingRepository services;

	@Autowired
	private StaffMemberRepository staff;

	@Test
	@DisplayName("The second of two simultaneous changes is refused instead of overwriting the first")
	void secondChangeIsRefused() throws Exception {
		long bookingId = createBooking();
		CountDownLatch salonHasRead = new CountDownLatch(1);
		CountDownLatch clientHasSaved = new CountDownLatch(1);

		try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
			// The salon reads the booking (still BOOKED), then waits before saving.
			Future<?> salon = threads.submit(() -> transactions.executeWithoutResult(status -> {
				Booking booking = bookings.findById(bookingId).orElseThrow();
				salonHasRead.countDown();
				await(clientHasSaved);
				booking.markCompleted();
			}));
			// Meanwhile the client cancels and saves first.
			Future<?> client = threads.submit(() -> {
				await(salonHasRead);
				transactions.executeWithoutResult(status -> bookings.findById(bookingId).orElseThrow().cancel());
				clientHasSaved.countDown();
			});

			client.get(10, TimeUnit.SECONDS);
			assertThat(catchCause(salon)).isInstanceOf(OptimisticLockingFailureException.class);
		}
		assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).as("the client's change is kept")
			.isEqualTo(BookingStatus.CANCELLED);
	}

	private long createBooking() {
		AppUser owner = users.save(new AppUser(AuthTestSupport.uniqueEmail(), "{noop}not-used", Instant.now()));
		Salon salon = salons
			.save(new Salon("Sipho's Cuts", new OpeningHours(LocalTime.of(9, 0), LocalTime.of(20, 0)), owner.getId()));
		ServiceOffering haircut = services
			.save(new ServiceOffering(salon, "Haircut", Duration.ofMinutes(30), null, null));
		StaffMember sipho = staff.save(new StaffMember(salon, "Sipho", salon.getOpeningHours()));
		return bookings
			.save(new Booking(haircut, sipho, "Thabo", "082 123 4567", LocalDate.of(2030, 1, 7), LocalTime.of(10, 0)))
			.getId();
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Timed out waiting for the other thread");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(ex);
		}
	}

	/** The exception the task failed with, or null if it succeeded. */
	private static Throwable catchCause(Future<?> task) throws InterruptedException {
		try {
			task.get(10, TimeUnit.SECONDS);
			return null;
		}
		catch (java.util.concurrent.ExecutionException ex) {
			return ex.getCause();
		}
		catch (java.util.concurrent.TimeoutException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
