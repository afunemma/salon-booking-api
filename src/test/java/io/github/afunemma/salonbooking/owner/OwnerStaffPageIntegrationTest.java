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
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import io.github.afunemma.salonbooking.AuthTestSupport;
import io.github.afunemma.salonbooking.FixedClockConfiguration;
import io.github.afunemma.salonbooking.TestcontainersConfiguration;

/**
 * The owner's "Staff & time off" page. "Now" is Sunday 2030-01-06 10:00, so Monday
 * 2030-01-07 is tomorrow. The salon is open 09:00–20:00 and starts with one staff member,
 * "Owner".
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class OwnerStaffPageIntegrationTest {

	private static final String MONDAY = "2030-01-07";

	private static final String[] EVERY_DAY = { "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY",
			"SUNDAY" };

	@Autowired
	private MockMvcTester mvc;

	private MockHttpSession session;

	private long salonId;

	private long haircutId;

	private long ownerStaffId;

	@BeforeEach
	void ownerWithSalon() {
		session = registerAndLogIn();
		String location = post("/owner/salons").param("name", "Sipho's Cuts")
			.param("opensAt", "09:00")
			.param("closesAt", "20:00")
			.exchange()
			.getResponse()
			.getRedirectedUrl();
		salonId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
		post("/owner/salons/" + salonId + "/services").param("name", "Haircut")
			.param("durationMinutes", "30")
			.exchange();
		haircutId = JsonPath.<Integer>read(
				AuthTestSupport.body(mvc.get().uri("/api/v1/salons/{id}/services", salonId).exchange()), "$[0].id");
		ownerStaffId = JsonPath.<Integer>read(
				AuthTestSupport.body(mvc.get().uri("/api/v1/salons/{id}/staff", salonId).exchange()), "$[0].id");
	}

	@Test
	@DisplayName("The page lists staff with their schedules, and an owner adds someone with days off and a break")
	void addStaff() {
		assertThat(page()).hasStatusOk()
			.bodyText()
			.contains("Owner", "Every day · 09:00–20:00", "No time off coming up.");

		assertThat(post("/owner/salons/" + salonId + "/staff").param("name", "Lindiwe")
			.param("startsAt", "12:00")
			.param("endsAt", "20:00")
			.param("workingDays", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY")
			.param("breakStartsAt", "15:00")
			.param("breakEndsAt", "15:30")
			.exchange()).hasStatus(HttpStatus.FOUND).flash().containsEntry("message", "Lindiwe was added.");

		assertThat(page()).bodyText().contains("Lindiwe", "Tue–Sat · 12:00–20:00", "break 15:00–15:30");
		assertThat(mvc.get().uri("/salons/{id}/book", salonId)).bodyText().contains("Lindiwe");
	}

	@Test
	@DisplayName("Forms show times and dates in the format HTML fields need, and each working day ticked")
	void editFormIsFilledIn() {
		assertThat(mvc.get().uri("/owner/salons/{s}/staff/{id}", salonId, ownerStaffId).session(session)).hasStatusOk()
			.bodyText()
			.contains("value=\"Owner\"", "value=\"09:00\"", "value=\"MONDAY\" checked=\"checked\"");
		assertThat(mvc.get()
			.uri("/owner/salons/{s}/staff/{id}", salonId, ownerStaffId)
			.session(session)
			.header("Accept-Language", "en-ZA")).as("the same for a South African browser")
			.bodyText()
			.contains("value=\"09:00\"");
		assertThat(page()).as("date fields need yyyy-MM-dd, or browsers show them empty")
			.bodyText()
			.contains("value=\"2030-01-07\"");
	}

	@Test
	@DisplayName("Mistakes are explained on the form: no working days, half a break, or hours outside opening hours")
	void scheduleMistakes() {
		assertThat(updateOwner("09:00", "20:00", new String[0], "", "")).hasStatusOk()
			.bodyText()
			.contains("choose at least one working day");
		assertThat(updateOwner("09:00", "20:00", EVERY_DAY, "13:00", "")).hasStatusOk()
			.bodyText()
			.contains("a break needs a start and an end");
		assertThat(updateOwner("07:00", "20:00", EVERY_DAY, "", "")).hasStatusOk()
			.bodyText()
			.contains("must be within the salon&#39;s opening hours 09:00–20:00");
	}

	@Test
	@DisplayName("A new day off that would strand a booking is refused, with the reason on the form")
	void scheduleChangeCantStrandBookings() {
		bookMondayAtTen();

		String[] noMondays = { "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY" };
		assertThat(updateOwner("09:00", "20:00", noMondays, "", "")).hasStatusOk()
			.bodyText()
			.contains("would fall outside the new schedule, the first on 2030-01-07 at 10:00");
	}

	@Test
	@DisplayName("The owner closes the salon for a day, clients see no free times, and removing it reopens the day")
	void timeOffAddAndRemove() {
		assertThat(addTimeOff("", MONDAY, MONDAY)).hasStatus(HttpStatus.FOUND)
			.flash()
			.containsEntry("message", "Time off added.");
		assertThat(page()).bodyText().contains("Whole salon", "Mon 7 Jan 2030", ">Remove<");
		assertThat(freeTimesOnMonday()).as("closed").doesNotContain("book/details?");

		String html = AuthTestSupport.body(page());
		String deletePath = html.substring(html.indexOf("/owner/salons/" + salonId + "/time-off/"));
		deletePath = deletePath.substring(0, deletePath.indexOf('"'));
		assertThat(post(deletePath).exchange()).hasStatus(HttpStatus.FOUND)
			.flash()
			.containsEntry("message", "Time off removed.");
		assertThat(freeTimesOnMonday()).as("open again").contains("book/details?");
	}

	@Test
	@DisplayName("Time off over a booking, already over, or ending before it starts is explained on the form")
	void timeOffMistakes() {
		bookMondayAtTen();

		assertThat(addTimeOff(String.valueOf(ownerStaffId), MONDAY, MONDAY)).hasStatusOk()
			.bodyText()
			.contains("1 active booking(s) fall in this time off");
		assertThat(addTimeOff("", "2030-01-01", "2030-01-02")).hasStatusOk().bodyText().contains("is already over");
		assertThat(addTimeOff("", "2030-01-09", MONDAY)).hasStatusOk()
			.bodyText()
			.contains("lastDay must not be before firstDay");
	}

	@Test
	@DisplayName("Another owner gets 'not found', and forms without the CSRF token are refused")
	void accessAndCsrf() {
		MockHttpSession other = registerAndLogIn();
		assertThat(mvc.get().uri("/owner/salons/{id}/staff", salonId).session(other)).hasStatus(HttpStatus.NOT_FOUND)
			.bodyText()
			.contains("We couldn't find that");

		assertThat(mvc.post()
			.uri("/owner/salons/{id}/time-off", salonId)
			.session(session)
			.param("firstDay", MONDAY)
			.param("lastDay", MONDAY)).hasStatus(HttpStatus.FORBIDDEN);
	}

	private MvcTestResult updateOwner(String startsAt, String endsAt, String[] days, String breakStart,
			String breakEnd) {
		return post("/owner/salons/" + salonId + "/staff/" + ownerStaffId).param("name", "Owner")
			.param("startsAt", startsAt)
			.param("endsAt", endsAt)
			.param("workingDays", days)
			.param("breakStartsAt", breakStart)
			.param("breakEndsAt", breakEnd)
			.exchange();
	}

	private MvcTestResult addTimeOff(String staffId, String firstDay, String lastDay) {
		return post("/owner/salons/" + salonId + "/time-off").param("staffId", staffId)
			.param("firstDay", firstDay)
			.param("lastDay", lastDay)
			.exchange();
	}

	private void bookMondayAtTen() {
		assertThat(AuthTestSupport.postJson(mvc, "/api/v1/salons/" + salonId + "/bookings",
				"""
						{"serviceId": %d, "clientName": "Thabo", "clientPhone": "082 123 4567", "date": "%s", "startTime": "10:00"}
						"""
					.formatted(haircutId, MONDAY)))
			.hasStatus(HttpStatus.CREATED);
	}

	private String freeTimesOnMonday() {
		return AuthTestSupport.body(mvc.get().uri("/salons/{id}/book?date={d}", salonId, MONDAY).exchange());
	}

	private MvcTestResult page() {
		return mvc.get().uri("/owner/salons/{id}/staff", salonId).session(session).exchange();
	}

	/** A form POST as the logged-in owner, with the CSRF token. */
	private MockMvcRequestBuilder post(String uri) {
		return mvc.post().uri(uri).session(session).with(csrf());
	}

	private MockHttpSession registerAndLogIn() {
		String email = AuthTestSupport.uniqueEmail();
		mvc.post()
			.uri("/owner/register")
			.with(csrf())
			.param("email", email)
			.param("password", AuthTestSupport.PASSWORD)
			.exchange();
		MvcTestResult login = mvc.post()
			.uri("/owner/login")
			.with(csrf())
			.param("email", email)
			.param("password", AuthTestSupport.PASSWORD)
			.exchange();
		assertThat(login).hasRedirectedUrl("/owner");
		return (MockHttpSession) login.getRequest().getSession(false);
	}

}
