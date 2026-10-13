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

import com.jayway.jsonpath.JsonPath;

import io.github.afunemma.salonbooking.AuthTestSupport;
import io.github.afunemma.salonbooking.FixedClockConfiguration;
import io.github.afunemma.salonbooking.TestcontainersConfiguration;

/**
 * Weekly days off and daily breaks, end to end. "Now" is Sunday 2030-01-06 10:00, so
 * tomorrow is Monday 2030-01-07. The salon is open 09:00–20:00; services take 30 minutes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class StaffScheduleIntegrationTest {

	private static final String MONDAY = "2030-01-07";

	private static final String TUESDAY = "2030-01-08";

	@Autowired
	private MockMvcTester mvc;

	private String owner;

	private long salonId;

	private long haircutId;

	private long staffId;

	@BeforeEach
	void createSalon() {
		owner = AuthTestSupport.registerAndLogin(mvc);
		salonId = idOf(post("/api/v1/salons", """
				{"name": "Sipho's Cuts", "opensAt": "09:00", "closesAt": "20:00"}
				"""));
		haircutId = idOf(post("/api/v1/salons/" + salonId + "/services", """
				{"name": "Haircut", "durationMinutes": 30}
				"""));
		staffId = JsonPath.<Integer>read(
				AuthTestSupport.body(mvc.get().uri("/api/v1/salons/{id}/staff", salonId).exchange()), "$[0].id");
	}

	@Test
	@DisplayName("By default staff work every day with no break")
	void defaultsToEveryDayWithoutBreak() {
		assertThat(mvc.get().uri("/api/v1/salons/{id}/staff", salonId).exchange()).bodyJson().isLenientlyEqualTo("""
				[{"workingDays": ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"],
				  "breakStartsAt": null, "breakEndsAt": null}]
				""");
	}

	@Test
	@DisplayName("No free times on a day off, and none that overlap the break")
	void daysOffAndBreaksRemoveFreeTimes() {
		assertThat(updateSchedule("""
				"workingDays": ["TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"],
				"breakStartsAt": "13:00", "breakEndsAt": "13:30"
				""")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"workingDays": ["TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"],
				 "breakStartsAt": "13:00:00", "breakEndsAt": "13:30:00"}
				""");

		assertThat(freeSlots(MONDAY)).bodyJson().extractingPath("$.startTimes").asArray().isEmpty();
		assertThat(freeSlots(TUESDAY)).bodyJson()
			.extractingPath("$.startTimes")
			.asArray()
			.contains("12:30:00", "13:30:00")
			.doesNotContain("12:45:00", "13:00:00", "13:15:00");
		assertThat(book(MONDAY, "10:00")).hasStatus(HttpStatus.CONFLICT);
		assertThat(book(TUESDAY, "13:00")).hasStatus(HttpStatus.CONFLICT);
		assertThat(book(TUESDAY, "13:30")).hasStatus(HttpStatus.CREATED);
	}

	@Test
	@DisplayName("A schedule change that would strand a booking is refused, naming the booking that clashes")
	void scheduleChangeCantStrandBookings() {
		assertThat(book(MONDAY, "13:00")).hasStatus(HttpStatus.CREATED);

		assertThat(updateSchedule("""
				"workingDays": ["TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"]
				""")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.isLenientlyEqualTo(
					"""
							{"title": "Schedule conflicts with bookings",
							 "detail": "1 active booking(s) would fall outside the new schedule, the first on 2030-01-07 at 13:00. Cancel them first, or change the schedule so they still fit."}
							""");
		assertThat(updateSchedule("""
				"breakStartsAt": "13:00", "breakEndsAt": "13:30"
				""")).as("break over the booking").hasStatus(HttpStatus.CONFLICT);
		assertThat(updateSchedule("""
				"breakStartsAt": "12:00", "breakEndsAt": "12:30"
				""")).as("break that misses it").hasStatusOk();
	}

	@Test
	@DisplayName("Invalid schedules are rejected with 400: no working days, half a break, or a break outside working hours")
	void invalidSchedules() {
		assertThat(updateSchedule("""
				"workingDays": []
				""")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(updateSchedule("""
				"breakStartsAt": "13:00"
				""")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(updateSchedule("""
				"startsAt": "09:00", "endsAt": "12:00", "breakStartsAt": "13:00", "breakEndsAt": "13:30"
				""")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.title")
			.isEqualTo("Invalid working hours");
	}

	/** Replaces the staff member's schedule; {@code fields} are added after the name. */
	private MvcTestResult updateSchedule(String fields) {
		return mvc.put()
			.uri("/api/v1/salons/{salon}/staff/{staff}", salonId, staffId)
			.header(AUTHORIZATION, owner)
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\": \"Sipho\", " + fields + "}")
			.exchange();
	}

	private MvcTestResult freeSlots(String date) {
		return mvc.get()
			.uri("/api/v1/salons/{id}/free-slots?serviceId={service}&date={date}", salonId, haircutId, date)
			.exchange();
	}

	private MvcTestResult book(String date, String startTime) {
		return AuthTestSupport.postJson(mvc, "/api/v1/salons/" + salonId + "/bookings", """
				{"serviceId": %d, "clientName": "Thabo", "clientPhone": "082 123 4567", "date": "%s", "startTime": "%s"}
				""".formatted(haircutId, date, startTime));
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
