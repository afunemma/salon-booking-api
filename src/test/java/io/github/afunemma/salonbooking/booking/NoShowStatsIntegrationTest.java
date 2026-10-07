package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import io.github.afunemma.salonbooking.AuthTestSupport;
import io.github.afunemma.salonbooking.FixedClockConfiguration;
import io.github.afunemma.salonbooking.TestcontainersConfiguration;
import io.github.afunemma.salonbooking.salon.ServiceOffering;
import io.github.afunemma.salonbooking.salon.ServiceOfferingRepository;

/**
 * The owner's no-show statistics, against a real PostgreSQL. "Today" is Sunday
 * 2030-01-06. Past bookings are saved directly, because the API (rightly) refuses to book
 * dates in the past.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class NoShowStatsIntegrationTest {

	private static final LocalDate THURSDAY = LocalDate.of(2030, 1, 3);

	private static final LocalDate FRIDAY = LocalDate.of(2030, 1, 4);

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private BookingRepository bookings;

	@Autowired
	private ServiceOfferingRepository services;

	private String owner;

	private long salonId;

	private ServiceOffering haircut;

	@BeforeEach
	void createSalonWithHaircut() {
		owner = AuthTestSupport.registerAndLogin(mvc);
		salonId = createSalon(owner);
		haircut = createHaircut(owner, salonId);
	}

	@Test
	@DisplayName("Rate counts no-shows out of completed + no-shows; cancelled, unmarked and future bookings are reported separately")
	void countsEachOutcome() {
		save(haircut, THURSDAY, 9, Booking::markCompleted);
		save(haircut, THURSDAY, 10, Booking::markCompleted);
		save(haircut, THURSDAY, 11, Booking::markNoShow);
		save(haircut, THURSDAY, 12, Booking::cancel);
		save(haircut, THURSDAY, 13, booking -> {
			// still BOOKED: the owner never recorded what happened
		});
		save(haircut, FRIDAY, 9, Booking::markCompleted);
		save(haircut, LocalDate.of(2030, 1, 7), 9, booking -> {
			// tomorrow: no outcome yet, so not "not marked"
		});
		save(haircut, LocalDate.of(2029, 12, 31), 9, Booking::markNoShow); // outside the
																			// range

		assertThat(stats(owner, salonId, "2030-01-01", "2030-01-07")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"from": "2030-01-01", "to": "2030-01-07", "completed": 3, "noShows": 1, "cancelled": 1,
				 "notMarked": 1, "noShowRatePercent": 25.0}
				""");
	}

	@Test
	@DisplayName("Other salons' bookings are never counted")
	void onlyCountsOwnSalon() {
		String otherOwner = AuthTestSupport.registerAndLogin(mvc);
		long otherSalon = createSalon(otherOwner);
		save(createHaircut(otherOwner, otherSalon), THURSDAY, 9, Booking::markNoShow);
		save(haircut, THURSDAY, 9, Booking::markCompleted);

		assertThat(stats(owner, salonId, "2030-01-01", "2030-01-05")).bodyJson().isLenientlyEqualTo("""
				{"completed": 1, "noShows": 0, "noShowRatePercent": 0.0}
				""");
	}

	@Test
	@DisplayName("With no appointments the rate is null, not a misleading 0%")
	void noAppointmentsMeansNoRate() {
		assertThat(stats(owner, salonId, "2030-01-01", "2030-01-05")).hasStatusOk()
			.bodyJson()
			.extractingPath("$.noShowRatePercent")
			.isNull();
	}

	@Test
	@DisplayName("A backwards range or one longer than a year is rejected")
	void invalidRangesAreRejected() {
		assertThat(stats(owner, salonId, "2030-01-05", "2030-01-01")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.title")
			.isEqualTo("Invalid date range");
		assertThat(stats(owner, salonId, "2029-01-01", "2030-01-02")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(stats(owner, salonId, "2029-01-01", "2030-01-01")).as("exactly 366 days").hasStatusOk();
	}

	@Test
	@DisplayName("Only the salon's owner can see its statistics")
	void ownerOnly() {
		String otherOwner = AuthTestSupport.registerAndLogin(mvc);

		assertThat(stats(otherOwner, salonId, "2030-01-01", "2030-01-05")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/v1/salons/{id}/no-show-stats?from=2030-01-01&to=2030-01-05", salonId).exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	private void save(ServiceOffering service, LocalDate date, int hour, Consumer<Booking> outcome) {
		Booking booking = new Booking(service, "Thabo", "082 123 4567", date, LocalTime.of(hour, 0));
		outcome.accept(booking);
		bookings.save(booking);
	}

	private MvcTestResult stats(String authorization, long salon, String from, String to) {
		return mvc.get()
			.uri("/api/v1/salons/{id}/no-show-stats?from={from}&to={to}", salon, from, to)
			.header(AUTHORIZATION, authorization)
			.exchange();
	}

	private long createSalon(String authorization) {
		return idOf(post(authorization, "/api/v1/salons", """
				{"name": "Sipho's Cuts", "opensAt": "08:00", "closesAt": "20:00"}
				"""));
	}

	private ServiceOffering createHaircut(String authorization, long salon) {
		long id = idOf(post(authorization, "/api/v1/salons/" + salon + "/services", """
				{"name": "Haircut", "durationMinutes": 35}
				"""));
		return services.findById(id).orElseThrow();
	}

	private MvcTestResult post(String authorization, String uri, String json) {
		return mvc.post()
			.uri(uri)
			.header(AUTHORIZATION, authorization)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.exchange();
	}

	private static long idOf(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.CREATED);
		String location = result.getResponse().getHeader("Location");
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

}
