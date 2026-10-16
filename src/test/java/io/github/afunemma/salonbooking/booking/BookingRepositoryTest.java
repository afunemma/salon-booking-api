package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

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
 * Runs against a real PostgreSQL in Docker (Testcontainers), with the schema created by
 * the Flyway migrations, so the tests catch SQL and mapping mistakes.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class BookingRepositoryTest {

	private static final LocalDate MONDAY = LocalDate.of(2026, 10, 5);

	@Autowired
	private AppUserRepository users;

	@Autowired
	private SalonRepository salons;

	@Autowired
	private ServiceOfferingRepository services;

	@Autowired
	private StaffMemberRepository staff;

	@Autowired
	private BookingRepository bookings;

	@Autowired
	private JdbcTemplate jdbc;

	private ServiceOffering haircut;

	private StaffMember sipho;

	@BeforeEach
	void createSalonWithHaircut() {
		AppUser owner = users.save(new AppUser(AuthTestSupport.uniqueEmail(), "{noop}not-used", Instant.now()));
		Salon salon = salons
			.save(new Salon("Sipho's Cuts", new OpeningHours(LocalTime.of(9, 0), LocalTime.of(20, 0)), owner.getId()));
		haircut = services.save(new ServiceOffering(salon, "Haircut", Duration.ofMinutes(35), 5000, 10000));
		sipho = staff.save(new StaffMember(salon, "Sipho", salon.getOpeningHours()));
	}

	@Test
	@DisplayName("A saved booking can be read back with its end time worked out from the service")
	void savesAndLoadsBooking() {
		Booking saved = bookings.save(new Booking(haircut, sipho, "Thabo", "0821234567", MONDAY, LocalTime.of(10, 0)));

		Booking loaded = bookings.findById(saved.getId()).orElseThrow();

		assertThat(loaded.getClientName()).isEqualTo("Thabo");
		assertThat(loaded.getEndTime()).isEqualTo(LocalTime.of(10, 35));
		assertThat(loaded.getStatus()).isEqualTo(BookingStatus.BOOKED);
	}

	@Test
	@DisplayName("Finding a day's active bookings skips cancelled ones and other days, in start-time order")
	void findsActiveBookingsForOneDay() {
		bookings.save(new Booking(haircut, sipho, "Sipho", "0820000002", MONDAY, LocalTime.of(14, 30)));
		bookings.save(new Booking(haircut, sipho, "Thabo", "0820000001", MONDAY, LocalTime.of(10, 0)));
		bookings.save(new Booking(haircut, sipho, "Lerato", "0820000003", MONDAY.plusDays(1), LocalTime.of(10, 0)));
		Booking cancelled = new Booking(haircut, sipho, "Zanele", "0820000004", MONDAY, LocalTime.of(12, 0));
		cancelled.cancel();
		bookings.save(cancelled);

		List<Booking> result = bookings.findBySalonIdAndBookingDateAndStatusOrderByStartTime(haircut.getSalon().getId(),
				MONDAY, BookingStatus.BOOKED);

		assertThat(result).extracting(Booking::getClientName).containsExactly("Thabo", "Sipho");
	}

	@Test
	@DisplayName("The database itself rejects overlapping active bookings for one staff member, even without the service's checks")
	void databaseRejectsOverlappingBookings() {
		bookings.saveAndFlush(new Booking(haircut, sipho, "Thabo", "0820000001", MONDAY, LocalTime.of(10, 0)));

		assertThatThrownBy(() -> bookings
			.saveAndFlush(new Booking(haircut, sipho, "Sipho", "0820000002", MONDAY, LocalTime.of(10, 15))))
			.isInstanceOf(DataIntegrityViolationException.class)
			// BookingService relies on this SQL error code and constraint name to
			// recognise overlaps.
			.rootCause()
			.isInstanceOfSatisfying(PSQLException.class, psql -> {
				assertThat(psql.getSQLState()).isEqualTo("23P01");
				assertThat(psql.getServerErrorMessage()).isNotNull()
					.extracting(ServerErrorMessage::getConstraint)
					.isEqualTo("booking_no_overlap");
			});
	}

	@Test
	@DisplayName("Back-to-back bookings, and bookings over a cancelled one, are allowed")
	void constraintAllowsBackToBackAndCancelledSlots() {
		Booking cancelled = new Booking(haircut, sipho, "Zanele", "0820000004", MONDAY, LocalTime.of(10, 0));
		cancelled.cancel();
		bookings.saveAndFlush(cancelled);

		bookings.saveAndFlush(new Booking(haircut, sipho, "Thabo", "0820000001", MONDAY, LocalTime.of(10, 0)));
		bookings.saveAndFlush(new Booking(haircut, sipho, "Sipho", "0820000002", MONDAY, LocalTime.of(10, 35)));

		assertThat(bookings.findBySalonIdAndBookingDateAndStatusOrderByStartTime(haircut.getSalon().getId(), MONDAY,
				BookingStatus.BOOKED))
			.hasSize(2);
	}

	@Test
	@DisplayName("Two staff members can be booked at the same time")
	void differentStaffCanOverlap() {
		StaffMember lindiwe = staff.save(new StaffMember(sipho.getSalon(), "Lindiwe", sipho.getWorkingHours()));

		bookings.saveAndFlush(new Booking(haircut, sipho, "Thabo", "0820000001", MONDAY, LocalTime.of(10, 0)));
		bookings.saveAndFlush(new Booking(haircut, lindiwe, "Sipho", "0820000002", MONDAY, LocalTime.of(10, 0)));

		assertThat(bookings.findBySalonIdAndBookingDateAndStatusOrderByStartTime(haircut.getSalon().getId(), MONDAY,
				BookingStatus.BOOKED))
			.hasSize(2);
	}

	@Test
	@DisplayName("The database rejects a booking with a staff member from another salon")
	void databaseRejectsStaffFromAnotherSalon() {
		AppUser owner = users.save(new AppUser(AuthTestSupport.uniqueEmail(), "{noop}not-used", Instant.now()));
		Salon other = salons
			.save(new Salon("Other Cuts", new OpeningHours(LocalTime.of(9, 0), LocalTime.of(20, 0)), owner.getId()));
		StaffMember elsewhere = staff.save(new StaffMember(other, "Lindiwe", other.getOpeningHours()));

		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO booking (salon_id, service_offering_id, staff_member_id, client_name, client_phone,
				                     client_phone_key, booking_date, start_time, end_time, status, created_at)
				VALUES (?, ?, ?, 'Thabo', '0820000001', '820000001', '2026-10-05', '10:00', '10:35', 'BOOKED', now())
				""", haircut.getSalon().getId(), haircut.getId(), elsewhere.getId()))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("booking_staff_in_same_salon");
	}

	@Test
	@DisplayName("The database rejects a price range where 'to' is lower than 'from'")
	void databaseChecksPriceRange() {
		Long salonId = haircut.getSalon().getId();

		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO service_offering (salon_id, name, duration_minutes, price_from_cents, price_to_cents)
				VALUES (?, 'Bad price', 30, 10000, 5000)
				""", salonId)).isInstanceOf(DataIntegrityViolationException.class);
	}

}
