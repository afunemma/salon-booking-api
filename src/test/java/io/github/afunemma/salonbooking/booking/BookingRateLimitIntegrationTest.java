package io.github.afunemma.salonbooking.booking;

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
 * At most 3 booking attempts per IP address per hour in this test (20 by default). Each
 * test uses its own made-up IP addresses, because the counts are shared across the
 * application.
 */
@SpringBootTest(properties = "app.booking.bookings-per-ip.attempts=3")
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class BookingRateLimitIntegrationTest {

	private static final String MONDAY = "2030-01-07";

	@Autowired
	private MockMvcTester mvc;

	private long salonId;

	private long haircutId;

	@BeforeEach
	void createSalon() {
		String owner = AuthTestSupport.registerAndLogin(mvc);
		salonId = idOf(post(owner, "/api/v1/salons", """
				{"name": "Sipho's Cuts", "opensAt": "09:00", "closesAt": "20:00"}
				"""));
		haircutId = idOf(post(owner, "/api/v1/salons/" + salonId + "/services", """
				{"name": "Haircut", "durationMinutes": 30}
				"""));
	}

	@Test
	@DisplayName("After the limit, one IP address gets 429 with Retry-After, while other addresses can still book")
	void limitPerIpAddress() {
		assertThat(book("10.0.0.1", "082 000 0001", "09:00")).hasStatus(HttpStatus.CREATED);
		assertThat(book("10.0.0.1", "082 000 0002", "09:00")).as("failed attempts count too")
			.hasStatus(HttpStatus.CONFLICT);
		assertThat(book("10.0.0.1", "082 000 0003", "10:00")).hasStatus(HttpStatus.CREATED);

		MvcTestResult refused = book("10.0.0.1", "082 000 0004", "11:00");
		assertThat(refused).hasStatus(HttpStatus.TOO_MANY_REQUESTS).bodyJson().isLenientlyEqualTo("""
				{"title": "Too many requests", "detail": "Too many bookings from your network. Please try again later."}
				""");
		assertThat(refused.getResponse().getHeader("Retry-After")).isNotBlank();

		assertThat(book("10.0.0.2", "082 000 0005", "11:00")).as("another IP address").hasStatus(HttpStatus.CREATED);
	}

	@Test
	@DisplayName("On the booking page, the limit is explained on the form with status 429")
	void bookingPageShowsTheLimit() {
		for (int i = 0; i < 3; i++) {
			bookOnPage("10.0.1.1", "082 100 000" + i, "%02d:00".formatted(9 + i));
		}

		MvcTestResult refused = bookOnPage("10.0.1.1", "082 100 0009", "13:00");
		assertThat(refused).hasStatus(HttpStatus.TOO_MANY_REQUESTS)
			.bodyText()
			.contains("Too many bookings from your network");
		assertThat(refused.getResponse().getHeader("Retry-After")).isNotBlank();
	}

	private MvcTestResult book(String ip, String phone, String startTime) {
		return mvc.post().uri("/api/v1/salons/{id}/bookings", salonId).with(request -> {
			request.setRemoteAddr(ip);
			return request;
		}).contentType(MediaType.APPLICATION_JSON).content("""
				{"serviceId": %d, "clientName": "Thabo", "clientPhone": "%s", "date": "%s", "startTime": "%s"}
				""".formatted(haircutId, phone, MONDAY, startTime)).exchange();
	}

	private MvcTestResult bookOnPage(String ip, String phone, String startTime) {
		return mvc.post().uri("/salons/{id}/book", salonId).with(request -> {
			request.setRemoteAddr(ip);
			return request;
		})
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("serviceId", String.valueOf(haircutId))
			.param("date", MONDAY)
			.param("startTime", startTime)
			.param("clientName", "Thabo")
			.param("clientPhone", phone)
			.exchange();
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
