package io.github.afunemma.salonbooking.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.github.afunemma.salonbooking.AuthTestSupport;
import io.github.afunemma.salonbooking.FixedClockConfiguration;
import io.github.afunemma.salonbooking.TestcontainersConfiguration;
import io.github.afunemma.salonbooking.account.AppUser;
import io.github.afunemma.salonbooking.account.AppUserRepository;
import io.github.afunemma.salonbooking.booking.Booking;
import io.github.afunemma.salonbooking.booking.BookingRepository;
import io.github.afunemma.salonbooking.booking.CancelLinks;
import io.github.afunemma.salonbooking.salon.Salon;
import io.github.afunemma.salonbooking.salon.SalonRepository;
import io.github.afunemma.salonbooking.salon.ServiceOffering;
import io.github.afunemma.salonbooking.salon.ServiceOfferingRepository;
import io.github.afunemma.salonbooking.scheduling.OpeningHours;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Runs the reminder job against a real PostgreSQL. "Today" is Sunday 6 January 2030 (see
 * {@link FixedClockConfiguration}), so reminders go out for Monday 7 January.
 * <p>
 * The sender is a Mockito mock, so the tests can see which messages were sent and make a
 * send fail.
 */
@SpringBootTest
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class ReminderJobIntegrationTest {

	private static final LocalDate TOMORROW = LocalDate.of(2030, 1, 7);

	@MockitoBean
	private ReminderSender sender;

	@Autowired
	private ReminderJob job;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private SalonRepository salons;

	@Autowired
	private ServiceOfferingRepository services;

	@Autowired
	private BookingRepository bookings;

	@Autowired
	private BookingReminderRepository reminders;

	@Autowired
	private MeterRegistry meters;

	@Autowired
	private CancelLinks cancelLinks;

	private ServiceOffering haircut;

	@BeforeEach
	void createSalonWithHaircut() {
		AppUser owner = users.save(new AppUser(AuthTestSupport.uniqueEmail(), "{noop}not-used", Instant.now()));
		Salon salon = salons
			.save(new Salon("Sipho's Cuts", new OpeningHours(LocalTime.of(9, 0), LocalTime.of(20, 0)), owner.getId()));
		haircut = services.save(new ServiceOffering(salon, "Haircut", Duration.ofMinutes(35), 5000, 10000));
	}

	@Test
	@DisplayName("Clients with an active booking tomorrow get one reminder, with the details of their appointment")
	void remindsTomorrowsActiveBookingsOnce() {
		Booking tomorrow = book("Thabo", TOMORROW, LocalTime.of(10, 0));
		Booking dayAfter = book("Lindiwe", TOMORROW.plusDays(1), LocalTime.of(10, 0));
		Booking cancelled = book("Ayanda", TOMORROW, LocalTime.of(11, 0));
		cancelled.cancel();
		bookings.save(cancelled);

		job.sendTomorrowsReminders();
		job.sendTomorrowsReminders();

		List<ReminderMessage> thabo = messagesFor(tomorrow);
		assertThat(thabo).as("sent once, not again on the second run").hasSize(1);
		assertThat(thabo.getFirst().phone()).isEqualTo("082 123 4567");
		assertThat(thabo.getFirst().text()).isEqualTo(
				"Hi Thabo, a reminder of your Haircut at Sipho's Cuts tomorrow, Monday 7 January, at 10:00. Can't make it? Cancel here so someone else can have the slot: "
						+ cancelLinks.urlFor(tomorrow.getId()));
		assertThat(messagesFor(dayAfter)).as("not tomorrow").isEmpty();
		assertThat(messagesFor(cancelled)).as("cancelled").isEmpty();

		BookingReminder reminder = reminders.findByBookingId(tomorrow.getId()).orElseThrow();
		assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.SENT);
		assertThat(reminder.getAttempts()).isEqualTo(1);
		assertThat(reminder.getSentAt()).isEqualTo(FixedClockConfiguration.NOW.toInstant());
	}

	@Test
	@DisplayName("A failed reminder is recorded and retried on the next run, without stopping the others")
	void retriesFailedReminder() {
		Booking failing = book("Thabo", TOMORROW, LocalTime.of(10, 0));
		Booking working = book("Lindiwe", TOMORROW, LocalTime.of(12, 0));
		doThrow(new IllegalStateException("provider down")).when(sender)
			.send(argThat(message -> message.bookingId().equals(failing.getId())));
		double failedBefore = count("failed");

		job.sendTomorrowsReminders();

		assertThat(reminders.findByBookingId(failing.getId()).orElseThrow().getStatus())
			.isEqualTo(ReminderStatus.FAILED);
		assertThat(reminders.findByBookingId(working.getId()).orElseThrow().getStatus()).isEqualTo(ReminderStatus.SENT);
		assertThat(count("failed")).isEqualTo(failedBefore + 1);

		doThrow(new IllegalStateException("still down")).doNothing()
			.when(sender)
			.send(argThat(message -> message.bookingId().equals(failing.getId())));
		job.sendTomorrowsReminders();
		job.sendTomorrowsReminders();

		BookingReminder reminder = reminders.findByBookingId(failing.getId()).orElseThrow();
		assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.SENT);
		assertThat(reminder.getAttempts()).isEqualTo(3);
		assertThat(messagesFor(working)).as("already sent, so not retried").hasSize(1);
	}

	private Booking book(String clientName, LocalDate date, LocalTime start) {
		return bookings.save(new Booking(haircut, clientName, "082 123 4567", date, start));
	}

	/** Every message the job passed to the sender for a booking. */
	private List<ReminderMessage> messagesFor(Booking booking) {
		return mockingDetails(sender).getInvocations()
			.stream()
			.map(invocation -> (ReminderMessage) invocation.getArgument(0))
			.filter(message -> message.bookingId().equals(booking.getId()))
			.toList();
	}

	private double count(String result) {
		var counter = meters.find("salon.reminders").tag("result", result).counter();
		return counter == null ? 0 : counter.count();
	}

}
