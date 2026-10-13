package io.github.afunemma.salonbooking.salon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Time;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Migration V5 runs on a database that already has salons and bookings, like the live
 * demo's. It must give every existing salon a staff member and move every booking to them
 * without losing anything.
 */
@Testcontainers
class StaffMigrationTest {

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

	@Test
	@DisplayName("Existing salons get an 'Owner' staff member with the salon's hours, and existing bookings move to them")
	void upgradesExistingData() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(),
				postgres.getPassword());
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		migrate(dataSource, "4");
		long salonId = insertSalonWithBooking(jdbc);

		migrate(dataSource, "latest");

		Map<String, Object> staff = jdbc.queryForMap("SELECT * FROM staff_member WHERE salon_id = ?", salonId);
		assertThat(staff).containsEntry("name", "Owner")
			.containsEntry("starts_at", Time.valueOf("09:00:00"))
			.containsEntry("ends_at", Time.valueOf("20:00:00"));
		assertThat(jdbc.queryForObject("SELECT staff_member_id FROM booking WHERE salon_id = ?", Long.class, salonId))
			.isEqualTo(staff.get("id"));

		// The overlap rule still holds after the upgrade, now per staff member.
		assertThatThrownBy(() -> jdbc.update("""
				INSERT INTO booking (salon_id, service_offering_id, staff_member_id, client_name, client_phone,
				                     booking_date, start_time, end_time, status, created_at)
				SELECT salon_id, service_offering_id, staff_member_id, 'Sipho', '0820000002',
				       booking_date, '10:15', '10:50', 'BOOKED', now()
				FROM booking WHERE salon_id = ?
				""", salonId)).isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("booking_no_overlap");
	}

	private static void migrate(DriverManagerDataSource dataSource, String target) {
		Flyway.configure().dataSource(dataSource).target(target).load().migrate();
	}

	private static long insertSalonWithBooking(JdbcTemplate jdbc) {
		long ownerId = jdbc.queryForObject("""
				INSERT INTO app_user (email, password_hash, created_at)
				VALUES ('owner@example.com', '{noop}x', now()) RETURNING id
				""", Long.class);
		long salonId = jdbc.queryForObject("""
				INSERT INTO salon (name, opens_at, closes_at, owner_id)
				VALUES ('Sipho''s Cuts', '09:00', '20:00', ?) RETURNING id
				""", Long.class, ownerId);
		long serviceId = jdbc.queryForObject("""
				INSERT INTO service_offering (salon_id, name, duration_minutes)
				VALUES (?, 'Haircut', 35) RETURNING id
				""", Long.class, salonId);
		jdbc.update("""
				INSERT INTO booking (salon_id, service_offering_id, client_name, client_phone,
				                     booking_date, start_time, end_time, status, created_at)
				VALUES (?, ?, 'Thabo', '0820000001', '2030-01-07', '10:00', '10:35', 'BOOKED', now())
				""", salonId, serviceId);
		return salonId;
	}

}
