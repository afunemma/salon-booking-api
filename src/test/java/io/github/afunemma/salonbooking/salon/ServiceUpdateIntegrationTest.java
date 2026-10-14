package io.github.afunemma.salonbooking.salon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

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

/**
 * Owners change a service's name, length or price. "Now" is Sunday 2030-01-06 10:00, so
 * Monday 2030-01-07 is tomorrow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class ServiceUpdateIntegrationTest {

	private static final String MONDAY = "2030-01-07";

	@Autowired
	private MockMvcTester mvc;

	private String owner;

	private long salonId;

	private long haircutId;

	@BeforeEach
	void createSalonWithHaircut() {
		owner = AuthTestSupport.registerAndLogin(mvc);
		salonId = idOf(send(owner, mvc.post().uri("/api/v1/salons"), """
				{"name": "Sipho's Cuts", "opensAt": "09:00", "closesAt": "20:00"}
				"""));
		haircutId = idOf(send(owner, mvc.post().uri("/api/v1/salons/{id}/services", salonId), """
				{"name": "Haircut", "durationMinutes": 30, "priceFromCents": 5000, "priceToCents": 10000}
				"""));
	}

	@Test
	@DisplayName("The owner changes the price, and clients see the new price straight away")
	void ownerChangesPrice() {
		assertThat(update(owner, """
				{"name": "Haircut", "durationMinutes": 30, "priceFromCents": 7550, "priceToCents": 7550}
				""")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"name": "Haircut", "priceFromCents": 7550, "priceToCents": 7550}
				""");

		assertThat(mvc.get().uri("/api/v1/salons/{id}/services", salonId).exchange()).bodyJson()
			.extractingPath("$[0].priceFromCents")
			.isEqualTo(7550);
		assertThat(mvc.get().uri("/salons/{id}/book", salonId).exchange()).bodyText()
			.as("the booking page shows rand and cents")
			.contains("Haircut · 30 min · R75.50");
	}

	@Test
	@DisplayName("A longer service only affects new bookings: existing ones keep their times")
	void lengthChangeKeepsExistingBookings() {
		assertThat(
				AuthTestSupport.postJson(mvc, "/api/v1/salons/" + salonId + "/bookings",
						"""
								{"serviceId": %d, "clientName": "Thabo", "clientPhone": "082 123 4567", "date": "%s", "startTime": "10:00"}
								"""
							.formatted(haircutId, MONDAY)))
			.bodyJson()
			.extractingPath("$.endTime")
			.isEqualTo("10:30:00");

		assertThat(update(owner, """
				{"name": "Haircut", "durationMinutes": 60}
				""")).hasStatusOk();

		assertThat(mvc.get()
			.uri("/api/v1/salons/{id}/bookings?date={d}", salonId, MONDAY)
			.header(AUTHORIZATION, owner)
			.exchange()).bodyJson().extractingPath("$[0].endTime").isEqualTo("10:30:00");
		assertThat(mvc.get()
			.uri("/api/v1/salons/{id}/free-slots?serviceId={s}&date={d}", salonId, haircutId, MONDAY)
			.exchange()).bodyJson().extractingPath("$.durationMinutes").isEqualTo(60);
	}

	@Test
	@DisplayName("Invalid prices are rejected, only the owner can change a service, and only their own")
	void rulesAndAccess() {
		assertThat(update(owner, """
				{"name": "Haircut", "durationMinutes": 30, "priceFromCents": 10000, "priceToCents": 5000}
				""")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(update(owner, """
				{"name": "Haircut", "durationMinutes": 30, "priceFromCents": -100}
				""")).hasStatus(HttpStatus.BAD_REQUEST);

		String otherOwner = AuthTestSupport.registerAndLogin(mvc);
		assertThat(update(otherOwner, """
				{"name": "Free haircut", "durationMinutes": 30, "priceFromCents": 0}
				""")).hasStatus(HttpStatus.FORBIDDEN);

		long otherSalon = idOf(send(otherOwner, mvc.post().uri("/api/v1/salons"), """
				{"name": "Other Cuts", "opensAt": "09:00", "closesAt": "20:00"}
				"""));
		assertThat(
				send(otherOwner, mvc.put().uri("/api/v1/salons/{salon}/services/{service}", otherSalon, haircutId), """
						{"name": "Stolen", "durationMinutes": 30}
						"""))
			.as("another salon's service")
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	private MvcTestResult update(String authorization, String json) {
		return send(authorization, mvc.put().uri("/api/v1/salons/{salon}/services/{service}", salonId, haircutId),
				json);
	}

	private MvcTestResult send(String authorization, MockMvcTester.MockMvcRequestBuilder request, String json) {
		return request.header(AUTHORIZATION, authorization)
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
