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
 * The client booking pages, end to end. "Now" is Sunday 2030-01-06 10:00, so the page
 * opens on Monday 2030-01-07. The salon is open 09:00–20:00.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class ClientBookingPageIntegrationTest {

	private static final String MONDAY = "2030-01-07";

	@Autowired
	private MockMvcTester mvc;

	private String owner;

	private long salonId;

	private long haircutId;

	@BeforeEach
	void createSalon() {
		owner = AuthTestSupport.registerAndLogin(mvc);
		salonId = idOf(post("/api/v1/salons", """
				{"name": "Sipho's Cuts", "opensAt": "09:00", "closesAt": "20:00"}
				"""));
		haircutId = idOf(post("/api/v1/salons/" + salonId + "/services", """
				{"name": "Haircut", "durationMinutes": 35, "priceFromCents": 5000, "priceToCents": 10000}
				"""));
	}

	@Test
	@DisplayName("The booking page opens on tomorrow, showing the services and free times as links")
	void showsServicesAndFreeTimes() {
		assertThat(mvc.get().uri("/salons/{id}/book", salonId).exchange()).hasStatusOk()
			.bodyText()
			.contains("Book at Sipho&#39;s Cuts", "Haircut · 35 min · R50–R100", "Monday 7 January", ">09:00<",
					"/book/details?serviceId=" + haircutId + "&amp;date=2030-01-07&amp;startTime=09:00")
			.as("one staff member, so no staff choice")
			.doesNotContain("Anyone");
	}

	@Test
	@DisplayName("Choosing a time, entering a name and phone books it and opens the client's own booking page")
	void bookingFlow() {
		assertThat(mvc.get()
			.uri("/salons/{id}/book/details?serviceId={s}&date={d}&startTime=10:00", salonId, haircutId, MONDAY)
			.exchange()).hasStatusOk().bodyText().contains("Monday 7 January 2030", "10:00", "Your name");

		MvcTestResult booked = book("Thabo", "082 123 4567", "10:00");

		assertThat(booked).hasStatus(HttpStatus.FOUND);
		String location = booked.getResponse().getRedirectedUrl();
		assertThat(location).contains("/bookings/").contains("/cancel?token=").endsWith("&booked=true");
		assertThat(mvc.get().uri(location.substring(location.indexOf("/bookings/"))).exchange()).hasStatusOk()
			.bodyText()
			.contains("You're booked!", "Hi Thabo", "with", "Owner", "Cancel my booking");
		assertThat(mvc.get().uri("/salons/{id}/book?date={d}", salonId, MONDAY).exchange()).bodyText()
			.as("10:00 is now taken")
			.doesNotContain(">10:00<");
	}

	@Test
	@DisplayName("A wrong phone number or a missing name shows the form again with the problem, and books nothing")
	void validationErrorsShownOnThePage() {
		assertThat(book("", "not a phone", "10:00")).hasStatusOk()
			.bodyText()
			.contains("must not be blank", "must be a phone number");
		assertThat(mvc.get().uri("/salons/{id}/book?date={d}", salonId, MONDAY).exchange()).bodyText()
			.contains(">10:00<");
	}

	@Test
	@DisplayName("If someone else took the time first, the client is told on the page and can choose another")
	void slotTakenShownOnThePage() {
		assertThat(book("Thabo", "082 123 4567", "10:00")).hasStatus(HttpStatus.FOUND);

		assertThat(book("Lindiwe", "082 765 4321", "10:00")).hasStatusOk()
			.bodyText()
			.contains("is not available", "Choose another time");
	}

	@Test
	@DisplayName("With more than one staff member, the client can choose who to book with")
	void staffChoice() {
		post("/api/v1/salons/" + salonId + "/staff", """
				{"name": "Lindiwe", "startsAt": "12:00", "endsAt": "20:00"}
				""");

		assertThat(mvc.get().uri("/salons/{id}/book", salonId).exchange()).bodyText()
			.contains("Anyone", "Owner", "Lindiwe");
	}

	@Test
	@DisplayName("Names are shown as text, never run, and an unknown salon gets a 'not found' page")
	void escapingAndNotFound() {
		long scriptSalon = idOf(post("/api/v1/salons", """
				{"name": "<script>alert(1)</script>", "opensAt": "09:00", "closesAt": "20:00"}
				"""));
		assertThat(mvc.get().uri("/salons/{id}/book", scriptSalon).exchange()).bodyText()
			.contains("&lt;script&gt;")
			.doesNotContain("<script>")
			.contains("hasn't added any services yet");

		assertThat(mvc.get().uri("/salons/999999/book").exchange()).hasStatus(HttpStatus.NOT_FOUND)
			.bodyText()
			.contains("We couldn't find that");
	}

	private MvcTestResult book(String name, String phone, String startTime) {
		return mvc.post()
			.uri("/salons/{id}/book", salonId)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("serviceId", String.valueOf(haircutId))
			.param("date", MONDAY)
			.param("startTime", startTime)
			.param("clientName", name)
			.param("clientPhone", phone)
			.exchange();
	}

	private MvcTestResult post(String uri, String json) {
		return mvc.post()
			.uri(uri)
			.header(AUTHORIZATION, owner)
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
