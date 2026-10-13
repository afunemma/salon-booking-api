package io.github.afunemma.salonbooking.salon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import io.github.afunemma.salonbooking.AuthTestSupport;
import io.github.afunemma.salonbooking.FixedClockConfiguration;
import io.github.afunemma.salonbooking.TestcontainersConfiguration;

/**
 * Dated time off, end to end. "Now" is Sunday 2030-01-06 10:00. The salon is open
 * 09:00–20:00 every day, with two staff: the owner and Lindiwe. Haircuts take 30 minutes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class TimeOffIntegrationTest {

	private static final String MONDAY = "2030-01-07";

	private static final String TUESDAY = "2030-01-08";

	private static final String WEDNESDAY = "2030-01-09";

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private JdbcTemplate jdbc;

	private String owner;

	private long salonId;

	private long haircutId;

	private long ownerStaffId;

	private long lindiweId;

	@BeforeEach
	void createSalonWithTwoStaff() {
		owner = AuthTestSupport.registerAndLogin(mvc);
		salonId = idOf(post(owner, "/api/v1/salons", """
				{"name": "Sipho's Cuts", "opensAt": "09:00", "closesAt": "20:00"}
				"""));
		haircutId = idOf(post(owner, "/api/v1/salons/" + salonId + "/services", """
				{"name": "Haircut", "durationMinutes": 30}
				"""));
		ownerStaffId = firstStaffId(salonId);
		lindiweId = idOf(post(owner, "/api/v1/salons/" + salonId + "/staff", """
				{"name": "Lindiwe"}
				"""));
	}

	@Test
	@DisplayName("A staff member on leave has no free times, so 'anyone' bookings go to someone else")
	void staffLeave() {
		assertThat(addTimeOff(lindiweId, MONDAY, MONDAY)).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.isLenientlyEqualTo("""
					{"staffName": "Lindiwe", "firstDay": "2030-01-07", "lastDay": "2030-01-07"}
					""");

		assertThat(freeSlots(MONDAY, lindiweId)).bodyJson().extractingPath("$.startTimes").asArray().isEmpty();
		assertThat(book(MONDAY, "10:00", lindiweId)).hasStatus(HttpStatus.CONFLICT);
		assertThat(book(MONDAY, "10:00", null)).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.staffId")
			.isEqualTo((int) ownerStaffId);
		assertThat(book(MONDAY, "10:00", null)).as("Lindiwe is away, so nobody is left").hasStatus(HttpStatus.CONFLICT);
		assertThat(freeSlots(TUESDAY, lindiweId)).bodyJson()
			.extractingPath("$.startTimes")
			.asArray()
			.contains("10:00:00");
	}

	@Test
	@DisplayName("Without a staff member, the whole salon is closed on every day of the period, both ends included")
	void salonClosure() {
		assertThat(addTimeOff(null, MONDAY, TUESDAY)).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.staffId")
			.isNull();

		assertThat(freeSlots(MONDAY, null)).bodyJson().extractingPath("$.startTimes").asArray().isEmpty();
		assertThat(freeSlots(TUESDAY, null)).bodyJson().extractingPath("$.startTimes").asArray().isEmpty();
		assertThat(freeSlots(WEDNESDAY, null)).bodyJson().extractingPath("$.startTimes").asArray().isNotEmpty();
	}

	@Test
	@DisplayName("Time off over existing bookings is refused, naming the first clash; other staff are unaffected")
	void timeOffCantStrandBookings() {
		assertThat(book(TUESDAY, "10:00", lindiweId)).hasStatus(HttpStatus.CREATED);

		assertThat(addTimeOff(lindiweId, MONDAY, WEDNESDAY)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson()
			.isLenientlyEqualTo(
					"""
							{"title": "Schedule conflicts with bookings",
							 "detail": "1 active booking(s) fall in this time off, the first on 2030-01-08 at 10:00. Cancel them first."}
							""");
		assertThat(addTimeOff(null, TUESDAY, TUESDAY)).as("closing the salon would strand it too")
			.hasStatus(HttpStatus.CONFLICT);
		assertThat(addTimeOff(ownerStaffId, MONDAY, WEDNESDAY)).as("the owner has no bookings then")
			.hasStatus(HttpStatus.CREATED);
	}

	@Test
	@DisplayName("The owner lists upcoming time off, soonest first, and can remove it to reopen those days")
	void listAndRemove() {
		long closure = idOf(addTimeOff(null, WEDNESDAY, WEDNESDAY));
		addTimeOff(lindiweId, MONDAY, TUESDAY);

		assertThat(listTimeOff(owner)).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				[{"staffName": "Lindiwe", "firstDay": "2030-01-07"}, {"staffId": null, "firstDay": "2030-01-09"}]
				""");

		assertThat(mvc.delete()
			.uri("/api/v1/salons/{salon}/time-off/{id}", salonId, closure)
			.header(AUTHORIZATION, owner)
			.exchange()).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(freeSlots(WEDNESDAY, null)).bodyJson().extractingPath("$.startTimes").asArray().isNotEmpty();
		assertThat(listTimeOff(owner)).bodyJson().extractingPath("$").asArray().hasSize(1);
		assertThat(mvc.delete()
			.uri("/api/v1/salons/{salon}/time-off/{id}", salonId, closure)
			.header(AUTHORIZATION, owner)
			.exchange()).as("already removed").hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("Backwards, finished or over-long time off is rejected with 400")
	void invalidTimeOff() {
		assertThat(addTimeOff(null, TUESDAY, MONDAY)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(addTimeOff(null, "2030-01-01", "2030-01-05")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.title")
			.isEqualTo("Invalid time off");
		assertThat(addTimeOff(null, MONDAY, "2031-01-08")).as("367 days").hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("Only the owner manages time off, and only for their own staff")
	void ownerOnly() {
		String otherOwner = AuthTestSupport.registerAndLogin(mvc);
		long otherSalon = idOf(post(otherOwner, "/api/v1/salons", """
				{"name": "Other Cuts", "opensAt": "09:00", "closesAt": "20:00"}
				"""));

		assertThat(post(otherOwner, "/api/v1/salons/" + salonId + "/time-off", """
				{"firstDay": "2030-01-07", "lastDay": "2030-01-07"}
				""")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(listTimeOff(otherOwner)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/v1/salons/{id}/time-off", salonId).exchange())
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(addTimeOff(firstStaffId(otherSalon), MONDAY, MONDAY)).as("another salon's staff member")
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("The database itself rejects time off for another salon's staff member, or ending before it starts")
	void databaseGuardsTimeOff() {
		String otherOwner = AuthTestSupport.registerAndLogin(mvc);
		long otherSalonsStaff = firstStaffId(idOf(post(otherOwner, "/api/v1/salons", """
				{"name": "Other Cuts", "opensAt": "09:00", "closesAt": "20:00"}
				""")));

		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO time_off (salon_id, staff_member_id, first_day, last_day) VALUES (?, ?, '2030-01-07', '2030-01-07')",
				salonId, otherSalonsStaff))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("time_off_staff_in_same_salon");
		assertThatThrownBy(() -> jdbc.update(
				"INSERT INTO time_off (salon_id, first_day, last_day) VALUES (?, '2030-01-08', '2030-01-07')", salonId))
			.isInstanceOf(DataIntegrityViolationException.class)
			.hasMessageContaining("time_off_first_before_last");
	}

	private MvcTestResult addTimeOff(@Nullable Long staffId, String firstDay, String lastDay) {
		return post(owner, "/api/v1/salons/" + salonId + "/time-off", """
				{"staffId": %s, "firstDay": "%s", "lastDay": "%s"}
				""".formatted(staffId, firstDay, lastDay));
	}

	private MvcTestResult listTimeOff(String authorization) {
		return mvc.get().uri("/api/v1/salons/{id}/time-off", salonId).header(AUTHORIZATION, authorization).exchange();
	}

	private MvcTestResult book(String date, String startTime, @Nullable Long staffId) {
		return AuthTestSupport.postJson(mvc, "/api/v1/salons/" + salonId + "/bookings", """
				{"serviceId": %d, "clientName": "Thabo", "clientPhone": "082 123 4567", "date": "%s",
				 "startTime": "%s", "staffId": %s}
				""".formatted(haircutId, date, startTime, staffId));
	}

	private MvcTestResult freeSlots(String date, @Nullable Long staffId) {
		String uri = "/api/v1/salons/{id}/free-slots?serviceId={service}&date={date}"
				+ (staffId != null ? "&staffId=" + staffId : "");
		return mvc.get().uri(uri, salonId, haircutId, date).exchange();
	}

	private long firstStaffId(long salon) {
		return JsonPath.<Integer>read(
				AuthTestSupport.body(mvc.get().uri("/api/v1/salons/{id}/staff", salon).exchange()), "$[0].id");
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
