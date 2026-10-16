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
import io.github.afunemma.salonbooking.salon.StaffMemberRepository;

/**
 * At most 3 upcoming bookings per phone number per salon (the default). "Now" is Sunday
 * 2030-01-06 10:00, so Monday 2030-01-07 is tomorrow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class BookingLimitIntegrationTest {

	private static final String MONDAY = "2030-01-07";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private BookingRepository bookings;

	@Autowired
	private ServiceOfferingRepository services;

	@Autowired
	private StaffMemberRepository staff;

	private String owner;

	private long salonId;

	private long haircutId;

	@BeforeEach
	void createSalon() {
		owner = AuthTestSupport.registerAndLogin(mvc);
		salonId = createSalon(owner);
		haircutId = createHaircut(owner, salonId);
	}

	@Test
	@DisplayName("A fourth upcoming booking for the same number is refused, however the number is typed")
	void limitPerPhoneNumber() {
		assertThat(book(salonId, haircutId, "082 123 4567", "09:00")).hasStatus(HttpStatus.CREATED);
		assertThat(book(salonId, haircutId, "0821234567", "10:00")).hasStatus(HttpStatus.CREATED);
		assertThat(book(salonId, haircutId, "+27 82 123 4567", "11:00")).hasStatus(HttpStatus.CREATED);

		assertThat(book(salonId, haircutId, "082 123 4567", "12:00")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.isLenientlyEqualTo(
					"""
							{"title": "Too many bookings",
							 "detail": "This phone number already has 3 upcoming booking(s) at this salon, the most allowed. Cancel one first, or contact the salon."}
							""");
		assertThat(book(salonId, haircutId, "083 000 0000", "12:00")).as("another number")
			.hasStatus(HttpStatus.CREATED);
	}

	@Test
	@DisplayName("The limit is per salon, and cancelling a booking frees a place")
	void perSalonAndCancellingFreesAPlace() {
		long first = idOf(book(salonId, haircutId, "082 123 4567", "09:00"));
		book(salonId, haircutId, "082 123 4567", "10:00");
		book(salonId, haircutId, "082 123 4567", "11:00");

		String otherOwner = AuthTestSupport.registerAndLogin(mvc);
		long otherSalon = createSalon(otherOwner);
		assertThat(book(otherSalon, createHaircut(otherOwner, otherSalon), "082 123 4567", "09:00")).as("another salon")
			.hasStatus(HttpStatus.CREATED);

		assertThat(mvc.post()
			.uri("/api/v1/salons/{salon}/bookings/{id}/cancel", salonId, first)
			.header(AUTHORIZATION, owner)
			.exchange()).hasStatusOk();
		assertThat(book(salonId, haircutId, "082 123 4567", "12:00")).hasStatus(HttpStatus.CREATED);
	}

	@Test
	@DisplayName("Past bookings don't count towards the limit")
	void pastBookingsDontCount() {
		var haircut = services.findById(haircutId).orElseThrow();
		var member = staff.findBySalonIdOrderById(salonId).getFirst();
		for (int hour = 9; hour < 12; hour++) {
			bookings.save(new Booking(haircut, member, "Thabo", "082 123 4567", LocalDate.of(2030, 1, 5),
					LocalTime.of(hour, 0)));
		}

		assertThat(book(salonId, haircutId, "082 123 4567", "09:00")).hasStatus(HttpStatus.CREATED);
	}

	@Test
	@DisplayName("On the booking page, the limit is explained on the form")
	void bookingPageShowsTheLimit() {
		book(salonId, haircutId, "082 123 4567", "09:00");
		book(salonId, haircutId, "082 123 4567", "10:00");
		book(salonId, haircutId, "082 123 4567", "11:00");

		assertThat(mvc.post()
			.uri("/salons/{id}/book", salonId)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("serviceId", String.valueOf(haircutId))
			.param("date", MONDAY)
			.param("startTime", "12:00")
			.param("clientName", "Thabo")
			.param("clientPhone", "082 123 4567")
			.exchange()).hasStatusOk().bodyText().contains("already has 3 upcoming booking(s)");
	}

	private MvcTestResult book(long salon, long service, String phone, String startTime) {
		return AuthTestSupport.postJson(mvc, "/api/v1/salons/" + salon + "/bookings", """
				{"serviceId": %d, "clientName": "Thabo", "clientPhone": "%s", "date": "%s", "startTime": "%s"}
				""".formatted(service, phone, MONDAY, startTime));
	}

	private long createSalon(String authorization) {
		return idOf(post(authorization, "/api/v1/salons", """
				{"name": "Sipho's Cuts", "opensAt": "09:00", "closesAt": "20:00"}
				"""));
	}

	private long createHaircut(String authorization, long salon) {
		return idOf(post(authorization, "/api/v1/salons/" + salon + "/services", """
				{"name": "Haircut", "durationMinutes": 30}
				"""));
	}

	private MvcTestResult post(String authorization, String uri, String json) {
		return mvc.post()
			.uri(uri)
			.header(AUTHORIZATION, authorization)
			.contentType(MediaType.APPLICATION_JSON)
			.content(json)
			.exchange();
	}

	/** The id from a Location header (salons, services) or from the body (bookings). */
	private static long idOf(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.CREATED);
		String location = result.getResponse().getHeader("Location");
		if (location != null) {
			return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
		}
		return JsonPath.<Integer>read(AuthTestSupport.body(result), "$.id");
	}

}
