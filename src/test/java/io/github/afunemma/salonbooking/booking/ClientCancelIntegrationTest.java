package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.time.LocalDate;
import java.time.LocalTime;

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

import com.jayway.jsonpath.JsonPath;

import io.github.afunemma.salonbooking.AuthTestSupport;
import io.github.afunemma.salonbooking.FixedClockConfiguration;
import io.github.afunemma.salonbooking.TestcontainersConfiguration;
import io.github.afunemma.salonbooking.salon.ServiceOfferingRepository;

/**
 * A client cancels their own booking from the link in their reminder, without an account.
 * "Now" is Sunday 2030-01-06 10:00, so Monday 2030-01-07 is tomorrow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class ClientCancelIntegrationTest {

	private static final String TOMORROW = "2030-01-07";

	/** The links use app.public-base-url; MockMvc only needs the path. */
	private static final String BASE_URL = "http://localhost:8080";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private BookingRepository bookings;

	@Autowired
	private ServiceOfferingRepository services;

	@Autowired
	private CancelLinks cancelLinks;

	private String owner;

	private long salonId;

	private long haircutId;

	@BeforeEach
	void createSalonWithHaircut() {
		owner = AuthTestSupport.registerAndLogin(mvc);
		salonId = idOf(asOwner("/api/v1/salons", """
				{"name": "Sipho's Cuts", "opensAt": "09:00", "closesAt": "20:00"}
				"""));
		haircutId = idOf(asOwner("/api/v1/salons/" + salonId + "/services", """
				{"name": "Haircut", "durationMinutes": 35}
				"""));
	}

	@Test
	@DisplayName("Booking returns a cancel link; opening it shows the appointment, and the button cancels it and frees the slot")
	void clientCancelsFromLink() {
		MvcTestResult booking = book("Thabo", "10:00");
		String link = cancelPath(booking);

		MvcTestResult page = mvc.get().uri(link).exchange();
		assertThat(page).hasStatusOk()
			.bodyText()
			.contains("Hi Thabo", "Haircut", "Sipho&#39;s Cuts", "Monday 7 January 2030", "10:00", "Cancel my booking");
		assertThat(status(booking)).as("opening the link changes nothing").isEqualTo("BOOKED");

		assertThat(mvc.post().uri(link).exchange()).as("after cancelling, back to the page")
			.hasStatus(HttpStatus.FOUND)
			.hasRedirectedUrl(link);

		assertThat(status(booking)).isEqualTo("CANCELLED");
		assertThat(mvc.get().uri(link).exchange()).bodyText()
			.contains("This booking is cancelled")
			.doesNotContain("Cancel my booking");
		assertThat(freeSlots()).bodyJson().extractingPath("$.startTimes").asArray().contains("10:00:00");
	}

	@Test
	@DisplayName("Pressing cancel twice is harmless")
	void cancellingTwiceIsHarmless() {
		String link = cancelPath(book("Thabo", "10:00"));

		assertThat(mvc.post().uri(link).exchange()).hasStatus(HttpStatus.FOUND);
		assertThat(mvc.post().uri(link).exchange()).hasStatus(HttpStatus.FOUND);
		assertThat(mvc.get().uri(link).exchange()).bodyText().contains("This booking is cancelled");
	}

	@Test
	@DisplayName("A wrong token, another booking's token or an unknown booking all get the same 'link not valid' page")
	void invalidLinksAreRejected() {
		MvcTestResult thabo = book("Thabo", "10:00");
		MvcTestResult lindiwe = book("Lindiwe", "11:00");
		long thaboId = JsonPath.<Integer>read(AuthTestSupport.body(thabo), "$.id");
		long lindiweId = JsonPath.<Integer>read(AuthTestSupport.body(lindiwe), "$.id");
		String lindiwesToken = cancelLinks.tokenFor(lindiweId);

		assertInvalid("/bookings/" + thaboId + "/cancel?token=wrong-token");
		assertInvalid("/bookings/" + thaboId + "/cancel?token=" + lindiwesToken);
		assertInvalid("/bookings/999999/cancel?token=" + cancelLinks.tokenFor(999999L));
		assertThat(mvc.post().uri("/bookings/" + thaboId + "/cancel?token=" + lindiwesToken).exchange())
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(status(thabo)).isEqualTo("BOOKED");
	}

	@Test
	@DisplayName("A booking that is done, or has already started, can't be cancelled from the link")
	void tooLateToCancel() {
		MvcTestResult done = book("Thabo", "10:00");
		long doneId = JsonPath.<Integer>read(AuthTestSupport.body(done), "$.id");
		assertThat(asOwner("/api/v1/salons/" + salonId + "/bookings/" + doneId + "/complete", "")).hasStatusOk();
		Booking started = bookings.save(new Booking(services.findById(haircutId).orElseThrow(), "Lindiwe",
				"082 123 4567", LocalDate.of(2030, 1, 6), LocalTime.of(9, 45)));
		String startedLink = "/bookings/" + started.getId() + "/cancel?token=" + cancelLinks.tokenFor(started.getId());

		for (String link : new String[] { cancelPath(done), startedLink }) {
			assertThat(mvc.get().uri(link).exchange()).hasStatusOk()
				.bodyText()
				.contains("can't be cancelled online")
				.doesNotContain("Cancel my booking");
			mvc.post().uri(link).exchange();
		}
		assertThat(status(done)).isEqualTo("COMPLETED");
		assertThat(bookings.findById(started.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.BOOKED);
	}

	@Test
	@DisplayName("Names are shown as text, never run as HTML, so a booking can't inject a script into the page")
	void namesAreEscaped() {
		String link = cancelPath(book("<script>alert(1)</script>", "10:00"));

		assertThat(mvc.get().uri(link).exchange()).bodyText()
			.contains("&lt;script&gt;alert(1)&lt;/script&gt;")
			.doesNotContain("<script>");
	}

	@Test
	@DisplayName("Only the client who booked gets the link; the owner's day view leaves it out")
	void dayViewHasNoCancelLinks() {
		book("Thabo", "10:00");

		MvcTestResult dayView = mvc.get()
			.uri("/api/v1/salons/{id}/bookings?date={date}", salonId, TOMORROW)
			.header(AUTHORIZATION, owner)
			.exchange();
		assertThat(dayView).hasStatusOk().bodyText().doesNotContain("cancelUrl");
	}

	private void assertInvalid(String link) {
		assertThat(mvc.get().uri(link).exchange()).hasStatus(HttpStatus.NOT_FOUND)
			.bodyText()
			.contains("This link isn't valid");
	}

	private MvcTestResult book(String clientName, String startTime) {
		MvcTestResult result = AuthTestSupport.postJson(mvc, "/api/v1/salons/" + salonId + "/bookings", """
				{"serviceId": %d, "clientName": "%s", "clientPhone": "082 123 4567", "date": "%s", "startTime": "%s"}
				""".formatted(haircutId, clientName, TOMORROW, startTime));
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return result;
	}

	/** The cancel link from a booking response, without the host. */
	private static String cancelPath(MvcTestResult booking) {
		String url = JsonPath.read(AuthTestSupport.body(booking), "$.cancelUrl");
		assertThat(url).startsWith(BASE_URL + "/bookings/");
		return url.substring(BASE_URL.length());
	}

	private String status(MvcTestResult booking) {
		long id = JsonPath.<Integer>read(AuthTestSupport.body(booking), "$.id");
		return bookings.findById(id).orElseThrow().getStatus().name();
	}

	private MvcTestResult freeSlots() {
		return mvc.get()
			.uri("/api/v1/salons/{id}/free-slots?serviceId={service}&date={date}", salonId, haircutId, TOMORROW)
			.exchange();
	}

	private MvcTestResult asOwner(String uri, String json) {
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
