package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

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

import io.github.afunemma.salonbooking.FixedClockConfiguration;
import io.github.afunemma.salonbooking.TestcontainersConfiguration;

/**
 * Exercises the API end to end: HTTP request → controller → service → real PostgreSQL.
 * "Now" is fixed at Sunday 2030-01-06 10:00, so Monday 2030-01-07 is tomorrow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class BookingApiIntegrationTest {

	private static final String TODAY = "2030-01-06";
	private static final String TOMORROW = "2030-01-07";

	@Autowired
	private MockMvcTester mvc;

	private long salonId;
	private long haircutId;

	@BeforeEach
	void createSalonWithHaircut() {
		MvcTestResult salon = post("/api/salons", """
				{"name": "Sipho's Cuts", "opensAt": "09:00", "closesAt": "20:00"}
				""");
		assertThat(salon).hasStatus(HttpStatus.CREATED);
		salonId = idOf(salon);

		MvcTestResult haircut = post("/api/salons/" + salonId + "/services", """
				{"name": "Haircut", "durationMinutes": 35, "priceFromCents": 5000, "priceToCents": 10000}
				""");
		assertThat(haircut).hasStatus(HttpStatus.CREATED);
		haircutId = idOf(haircut);
	}

	@Test
	@DisplayName("A client can see free times, book one, and it then disappears from the free list")
	void bookingFlow() {
		// 09:00 to 19:15 every 15 minutes = 42 start times (19:15 + 35 min ends at 19:50)
		assertThat(freeSlots(TOMORROW)).bodyJson().extractingPath("$.startTimes").asArray()
				.contains("10:00:00", "10:15:00").hasSize(42);

		MvcTestResult booking = book(TOMORROW, "10:00");

		assertThat(booking).hasStatus(HttpStatus.CREATED)
				.bodyJson().extractingPath("$.endTime").isEqualTo("10:35:00");
		assertThat(booking).bodyJson().extractingPath("$.status").isEqualTo("BOOKED");
		// 10:00 is taken, and 10:15 would overlap it
		assertThat(freeSlots(TOMORROW)).bodyJson().extractingPath("$.startTimes").asArray()
				.doesNotContain("10:00:00", "10:15:00", "10:30:00").contains("10:45:00");
		assertThat(mvc.get().uri("/api/salons/{id}/bookings?date={date}", salonId, TOMORROW))
				.hasStatusOk().bodyJson().extractingPath("$[0].clientName").isEqualTo("Thabo");
	}

	@Test
	@DisplayName("Booking a taken slot returns 409 Conflict with a clear message")
	void doubleBookingIsRejected() {
		assertThat(book(TOMORROW, "10:00")).hasStatus(HttpStatus.CREATED);

		MvcTestResult second = book(TOMORROW, "10:15");

		assertThat(second).hasStatus(HttpStatus.CONFLICT)
				.bodyJson().extractingPath("$.title").isEqualTo("Slot unavailable");
	}

	@Test
	@DisplayName("Cancelling frees the slot again, and a cancelled booking can't be cancelled twice")
	void cancelFreesSlot() {
		long bookingId = idOf(book(TOMORROW, "10:00"));
		String cancelUri = "/api/salons/" + salonId + "/bookings/" + bookingId + "/cancel";

		assertThat(mvc.post().uri(cancelUri)).hasStatusOk()
				.bodyJson().extractingPath("$.status").isEqualTo("CANCELLED");
		assertThat(freeSlots(TOMORROW)).bodyJson().extractingPath("$.startTimes").asArray().contains("10:00:00");
		assertThat(mvc.post().uri(cancelUri)).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	@DisplayName("Today only offers times that haven't passed yet")
	void todayHidesPastTimes() {
		// It is 10:00 now, so the first slot offered is 10:15.
		assertThat(freeSlots(TODAY)).bodyJson().extractingPath("$.startTimes[0]").isEqualTo("10:15:00");
	}

	@Test
	@DisplayName("Bookings in the past are rejected")
	void pastDateIsRejected() {
		assertThat(book("2030-01-05", "10:00")).hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson().extractingPath("$.detail").asString().contains("past");
	}

	@Test
	@DisplayName("A time the salon doesn't offer (off the 15-minute grid) is rejected")
	void offGridTimeIsRejected() {
		assertThat(book(TOMORROW, "10:07")).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	@DisplayName("Invalid input returns 400 listing each bad field")
	void validationErrorsListFields() {
		MvcTestResult result = post("/api/salons/" + salonId + "/bookings", """
				{"serviceId": %d, "clientName": "", "clientPhone": "call me", "date": "%s", "startTime": "10:00"}
				""".formatted(haircutId, TOMORROW));

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.errors.clientName").isNotNull();
		assertThat(result).bodyJson().extractingPath("$.errors.clientPhone").asString().contains("phone number");
	}

	@Test
	@DisplayName("A salon that closes before it opens is rejected")
	void invalidOpeningHoursAreRejected() {
		assertThat(post("/api/salons", """
				{"name": "Backwards", "opensAt": "20:00", "closesAt": "09:00"}
				""")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("Unknown salons return 404, and one salon can't book another salon's service")
	void notFound() {
		assertThat(mvc.get().uri("/api/salons/999999")).hasStatus(HttpStatus.NOT_FOUND);

		long otherSalonId = idOf(post("/api/salons", """
				{"name": "Other Salon", "opensAt": "09:00", "closesAt": "17:00"}
				"""));
		MvcTestResult result = post("/api/salons/" + otherSalonId + "/bookings", bookingJson(TOMORROW, "10:00"));

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
	}

	private MvcTestResult freeSlots(String date) {
		return mvc.get().uri("/api/salons/{id}/free-slots?serviceId={service}&date={date}", salonId, haircutId, date)
				.exchange();
	}

	private MvcTestResult book(String date, String startTime) {
		return post("/api/salons/" + salonId + "/bookings", bookingJson(date, startTime));
	}

	private String bookingJson(String date, String startTime) {
		return """
				{"serviceId": %d, "clientName": "Thabo", "clientPhone": "082 123 4567", "date": "%s", "startTime": "%s"}
				""".formatted(haircutId, date, startTime);
	}

	private MvcTestResult post(String uri, String json) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	/** Reads the new resource's id from the Location header, e.g. /api/salons/42 → 42. */
	private static long idOf(MvcTestResult result) {
		String location = result.getResponse().getHeader("Location");
		return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}
}
