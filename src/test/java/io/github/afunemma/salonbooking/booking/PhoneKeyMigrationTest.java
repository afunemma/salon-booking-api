package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Migration V8 runs on a database that already has bookings, like the live demo's, and
 * must fill in the phone key exactly as {@link Booking#phoneKey} would.
 */
@Testcontainers
class PhoneKeyMigrationTest {

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@Test
	@DisplayName("Existing bookings get the same phone key the Java code computes")
	void fillsPhoneKeyForExistingBookings() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		Flyway.configure().dataSource(dataSource).target("7").load().migrate();
		long ownerId = jdbc.queryForObject(
				"INSERT INTO app_user (email, password_hash, created_at) VALUES ('o@example.com', '{noop}x', now()) RETURNING id",
				Long.class);
		long salonId = jdbc.queryForObject(
				"INSERT INTO salon (name, opens_at, closes_at, owner_id) VALUES ('Cuts', '09:00', '20:00', ?) RETURNING id",
				Long.class, ownerId);
		long serviceId = jdbc.queryForObject(
				"INSERT INTO service_offering (salon_id, name, duration_minutes) VALUES (?, 'Haircut', 30) RETURNING id",
				Long.class, salonId);
		long staffId = jdbc.queryForObject(
				"INSERT INTO staff_member (salon_id, name, starts_at, ends_at) VALUES (?, 'Owner', '09:00', '20:00') RETURNING id",
				Long.class, salonId);
		String[] phones = { "082 123 4567", "+27 82 123 4567", "1234567" };
		for (int i = 0; i < phones.length; i++) {
			jdbc.update("""
					INSERT INTO booking (salon_id, service_offering_id, staff_member_id, client_name, client_phone,
					                     booking_date, start_time, end_time, status, created_at)
					VALUES (?, ?, ?, 'Thabo', ?, '2030-01-07', ?::time, ?::time, 'BOOKED', now())
					""", salonId, serviceId, staffId, phones[i], "%02d:00".formatted(9 + i),
					"%02d:30".formatted(9 + i));
		}

		Flyway.configure().dataSource(dataSource).load().migrate();

		for (String phone : phones) {
			assertThat(jdbc.queryForObject("SELECT client_phone_key FROM booking WHERE client_phone = ?", String.class,
					phone))
				.as(phone)
				.isEqualTo(Booking.phoneKey(phone));
		}
	}

}
