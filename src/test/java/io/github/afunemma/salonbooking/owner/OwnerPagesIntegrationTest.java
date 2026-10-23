package io.github.afunemma.salonbooking.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import io.github.afunemma.salonbooking.AuthTestSupport;
import io.github.afunemma.salonbooking.FixedClockConfiguration;
import io.github.afunemma.salonbooking.TestcontainersConfiguration;

/**
 * The owner pages end to end, logging in through the real form and keeping the session
 * like a browser. "Now" is Sunday 2030-01-06 10:00, so Monday 2030-01-07 is tomorrow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class OwnerPagesIntegrationTest {

	private static final String MONDAY = "2030-01-07";

	@Autowired
	private MockMvcTester mvc;

	private String email;

	@BeforeEach
	void registerOwner() {
		email = AuthTestSupport.uniqueEmail();
		assertThat(mvc.post()
			.uri("/owner/register")
			.with(csrf())
			.param("email", email)
			.param("password", AuthTestSupport.PASSWORD)).hasStatus(HttpStatus.FOUND)
			.hasRedirectedUrl("/owner/login?registered");
	}

	@Test
	@DisplayName("Owner pages need a login; a wrong password gets the same answer as an unknown email")
	void loginRequired() {
		assertThat(mvc.get().uri("/owner")).hasStatus(HttpStatus.FOUND).redirectedUrl().endsWith("/owner/login");

		assertThat(login(email, "wrong-password-123")).hasRedirectedUrl("/owner/login?error");
		assertThat(login("nobody@example.com", "wrong-password-123")).hasRedirectedUrl("/owner/login?error");
		assertThat(mvc.get().uri("/owner/login?error")).bodyText().contains("Wrong email or password.");
	}

	@Test
	@DisplayName("After logging in, the owner creates a salon and sees its empty day")
	void loginAndCreateSalon() {
		MockHttpSession session = loggedIn();

		assertThat(mvc.get().uri("/owner").session(session)).hasStatusOk()
			.bodyText()
			.contains("Logged in as " + email, "You don't have a salon yet");

		MvcTestResult created = mvc.post()
			.uri("/owner/salons")
			.session(session)
			.with(csrf())
			.param("name", "Sipho's Cuts")
			.param("opensAt", "09:00")
			.param("closesAt", "20:00")
			.exchange();
		assertThat(created).hasStatus(HttpStatus.FOUND);
		String salonPage = created.getResponse().getRedirectedUrl();
		assertThat(mvc.get().uri(salonPage + "?date=" + MONDAY).session(session)).hasStatusOk()
			.bodyText()
			.contains("Sipho&#39;s Cuts", "No bookings on this day.", "Monday 7 January 2030");
	}

	@Test
	@DisplayName("The day view lists bookings with a call link, and 'Done' marks one completed")
	void manageTheDay() {
		MockHttpSession session = loggedIn();
		long salonId = createSalonWithBooking(session);

		assertThat(dayView(session, salonId)).bodyText()
			.contains("10:00–10:30", "Thabo", "tel:082 123 4567", "Haircut with Owner", "Booked", ">Done<");

		long bookingId = firstBookingId(session, salonId);
		MvcTestResult done = mvc.post()
			.uri("/owner/salons/{s}/bookings/{b}/complete?date={d}", salonId, bookingId, MONDAY)
			.session(session)
			.with(csrf())
			.exchange();
		assertThat(done).hasStatus(HttpStatus.FOUND)
			.hasRedirectedUrl("/owner/salons/" + salonId + "?date=" + MONDAY)
			.flash()
			.containsEntry("message", "Booking updated.");
		assertThat(dayView(session, salonId)).bodyText().contains("Completed").doesNotContain(">Done<");

		assertThat(mvc.post()
			.uri("/owner/salons/{s}/bookings/{b}/cancel?date={d}", salonId, bookingId, MONDAY)
			.session(session)
			.with(csrf())
			.exchange()).as("already completed").flash().containsKey("error");
	}

	@Test
	@DisplayName("The day view shows the full booking link, with Copy and Share on WhatsApp buttons")
	void shareBookingLink() {
		MockHttpSession session = loggedIn();
		long salonId = createSalonWithBooking(session);

		String bookingUrl = "http://localhost:8080/salons/" + salonId + "/book";
		assertThat(dayView(session, salonId)).bodyText()
			.contains("value=\"" + bookingUrl + "\"", "Copy link")
			// Spaces as %20 and the link itself encoded, so WhatsApp gets the whole
			// message.
			.contains("https://wa.me/?text=Book%20your%20next%20appointment%20at%20Sipho%27s%20Cuts%3A%20"
					+ "http%3A%2F%2Flocalhost%3A8080%2Fsalons%2F" + salonId + "%2Fbook");
	}

	@Test
	@DisplayName("Forms without the CSRF token are refused, so other websites can't act for a logged-in owner")
	void csrfProtection() {
		MockHttpSession session = loggedIn();

		assertThat(mvc.post()
			.uri("/owner/salons")
			.session(session)
			.param("name", "Forged")
			.param("opensAt", "09:00")
			.param("closesAt", "20:00")).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	@DisplayName("Another owner's salon looks like one that doesn't exist")
	void otherOwnersSalon() {
		long theirSalon = createSalonWithBooking(loggedIn());

		String otherEmail = AuthTestSupport.uniqueEmail();
		mvc.post()
			.uri("/owner/register")
			.with(csrf())
			.param("email", otherEmail)
			.param("password", AuthTestSupport.PASSWORD)
			.exchange();
		MockHttpSession other = sessionOf(login(otherEmail, AuthTestSupport.PASSWORD));

		assertThat(mvc.get().uri("/owner/salons/{id}", theirSalon).session(other)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyText()
			.contains("We couldn't find that");
	}

	@Test
	@DisplayName("Repeated wrong passwords hit the same rate limit as the API, and the page says so")
	void loginRateLimit() {
		for (int i = 0; i < 5; i++) {
			login(email, "wrong-password-123");
		}

		assertThat(login(email, AuthTestSupport.PASSWORD)).hasRedirectedUrl("/owner/login?error=too-many");
		assertThat(mvc.get().uri("/owner/login?error=too-many")).bodyText().contains("Too many login attempts");
	}

	@Test
	@DisplayName("Logging out ends the session")
	void logout() {
		MockHttpSession session = loggedIn();

		assertThat(mvc.post().uri("/owner/logout").session(session).with(csrf()))
			.hasRedirectedUrl("/owner/login?logout");
		assertThat(mvc.get().uri("/owner").session(session)).hasStatus(HttpStatus.FOUND);
	}

	@Test
	@DisplayName("The API stays stateless: no session cookie, and an API token doesn't open the owner pages")
	void apiStaysStateless() {
		MvcTestResult apiLogin = AuthTestSupport.postJson(mvc, "/api/v1/auth/login", """
				{"email": "%s", "password": "%s"}
				""".formatted(email, AuthTestSupport.PASSWORD));
		assertThat(apiLogin).hasStatusOk();
		assertThat(apiLogin.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();

		String token = AuthTestSupport.registerAndLogin(mvc);
		assertThat(mvc.get().uri("/owner").header(HttpHeaders.AUTHORIZATION, token)).hasStatus(HttpStatus.FOUND);
	}

	private MvcTestResult login(String loginEmail, String password) {
		return mvc.post()
			.uri("/owner/login")
			.with(csrf())
			.param("email", loginEmail)
			.param("password", password)
			.exchange();
	}

	private MockHttpSession loggedIn() {
		MvcTestResult result = login(email, AuthTestSupport.PASSWORD);
		assertThat(result).hasRedirectedUrl("/owner");
		return sessionOf(result);
	}

	private static MockHttpSession sessionOf(MvcTestResult result) {
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	/**
	 * Creates a salon through the page, adds a haircut through the API, and books 10:00.
	 */
	private long createSalonWithBooking(MockHttpSession session) {
		String location = mvc.post()
			.uri("/owner/salons")
			.session(session)
			.with(csrf())
			.param("name", "Sipho's Cuts")
			.param("opensAt", "09:00")
			.param("closesAt", "20:00")
			.exchange()
			.getResponse()
			.getRedirectedUrl();
		long salonId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
		String token = "Bearer "
				+ JsonPath.read(AuthTestSupport.body(AuthTestSupport.postJson(mvc, "/api/v1/auth/login", """
						{"email": "%s", "password": "%s"}
						""".formatted(email, AuthTestSupport.PASSWORD))), "$.accessToken");
		MvcTestResult service = mvc.post()
			.uri("/api/v1/salons/{id}/services", salonId)
			.header(HttpHeaders.AUTHORIZATION, token)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "Haircut", "durationMinutes": 30}
					""")
			.exchange();
		String serviceLocation = service.getResponse().getHeader(HttpHeaders.LOCATION);
		long serviceId = Long.parseLong(serviceLocation.substring(serviceLocation.lastIndexOf('/') + 1));
		assertThat(AuthTestSupport.postJson(mvc, "/api/v1/salons/" + salonId + "/bookings",
				"""
						{"serviceId": %d, "clientName": "Thabo", "clientPhone": "082 123 4567", "date": "%s", "startTime": "10:00"}
						"""
					.formatted(serviceId, MONDAY)))
			.hasStatus(HttpStatus.CREATED);
		return salonId;
	}

	private MvcTestResult dayView(MockHttpSession session, long salonId) {
		return mvc.get().uri("/owner/salons/{id}?date={d}", salonId, MONDAY).session(session).exchange();
	}

	private long firstBookingId(MockHttpSession session, long salonId) {
		String html = AuthTestSupport.body(dayView(session, salonId));
		String marker = "/bookings/";
		int start = html.indexOf(marker, html.indexOf("/owner/salons/" + salonId + "/bookings/")) + marker.length();
		return Long.parseLong(html.substring(start, html.indexOf('/', start)));
	}

}
