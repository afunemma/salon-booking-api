package io.github.afunemma.salonbooking.salon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import org.jspecify.annotations.Nullable;
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
 * Staff members end to end: each has their own working hours and their own bookings.
 * "Now" is Sunday 2030-01-06 10:00, so Monday 2030-01-07 is tomorrow. The salon is open
 * 09:00–20:00; the owner works all day and Lindiwe works 12:00–20:00.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class StaffApiIntegrationTest {

	private static final String TOMORROW = "2030-01-07";

	@Autowired
	private MockMvcTester mvc;

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
				{"name": "Lindiwe", "startsAt": "12:00", "endsAt": "20:00"}
				"""));
	}

	@Test
	@DisplayName("A new salon starts with one staff member, 'Owner', working the salon's hours; anyone can list staff")
	void newSalonHasOwnerAsStaff() {
		assertThat(mvc.get().uri("/api/v1/salons/{id}/staff", salonId).exchange()).hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("""
					[{"name": "Owner", "startsAt": "09:00:00", "endsAt": "20:00:00"},
					 {"name": "Lindiwe", "startsAt": "12:00:00", "endsAt": "20:00:00"}]
					""");
	}

	@Test
	@DisplayName("Free slots follow each staff member's own hours; without a staff member, a time is free if anyone is free")
	void freeSlotsPerStaffMember() {
		assertThat(freeSlots(lindiweId)).bodyJson().extractingPath("$.startTimes[0]").isEqualTo("12:00:00");
		assertThat(freeSlots(null)).bodyJson()
			.extractingPath("$.startTimes")
			.asArray()
			.contains("09:00:00", "12:00:00")
			.doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("Two clients can book the same time with different staff; a third gets 409 once everyone is busy")
	void sameTimeWithDifferentStaff() {
		assertThat(book("12:00", null)).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.staffName")
			.as("the first free staff member, in the order they were added")
			.isEqualTo("Owner");
		assertThat(book("12:00", null)).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.staffName")
			.isEqualTo("Lindiwe");
		assertThat(book("12:00", null)).hasStatus(HttpStatus.CONFLICT);

		assertThat(freeSlots(null)).bodyJson()
			.extractingPath("$.startTimes")
			.asArray()
			.doesNotContain("12:00:00", "12:15:00")
			.contains("12:30:00");
	}

	@Test
	@DisplayName("A client can choose a staff member, but not outside that person's working hours")
	void bookSpecificStaffMember() {
		assertThat(book("10:00", lindiweId)).as("Lindiwe starts at 12:00").hasStatus(HttpStatus.CONFLICT);
		assertThat(book("10:00", null)).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.staffId")
			.isEqualTo((int) ownerStaffId);
		assertThat(book("13:00", lindiweId)).hasStatus(HttpStatus.CREATED)
			.bodyJson()
			.extractingPath("$.staffName")
			.isEqualTo("Lindiwe");
	}

	@Test
	@DisplayName("The owner can rename a staff member and change their hours")
	void updateStaffMember() {
		MvcTestResult renamed = mvc.put()
			.uri("/api/v1/salons/{salon}/staff/{staff}", salonId, ownerStaffId)
			.header(AUTHORIZATION, owner)
			.contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"name": "Sipho", "startsAt": "09:00", "endsAt": "14:00"}
					""")
			.exchange();

		assertThat(renamed).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"name": "Sipho", "startsAt": "09:00:00", "endsAt": "14:00:00"}
				""");
		// A 30-minute haircut must finish by 14:00, so 13:30 is the last start time.
		assertThat(freeSlots(ownerStaffId)).bodyJson().extractingPath("$.startTimes[-1]").isEqualTo("13:30:00");
	}

	@Test
	@DisplayName("Working hours must be within the salon's opening hours and start before they end")
	void invalidWorkingHours() {
		assertThat(post(owner, "/api/v1/salons/" + salonId + "/staff", """
				{"name": "Night owl", "startsAt": "18:00", "endsAt": "22:00"}
				""")).hasStatus(HttpStatus.BAD_REQUEST)
			.bodyJson()
			.extractingPath("$.title")
			.isEqualTo("Invalid working hours");
		assertThat(post(owner, "/api/v1/salons/" + salonId + "/staff", """
				{"name": "Backwards", "startsAt": "15:00", "endsAt": "12:00"}
				""")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	@DisplayName("Only the owner manages staff, and one salon's staff can't be used at another salon")
	void staffBelongsToOneSalon() {
		String otherOwner = AuthTestSupport.registerAndLogin(mvc);
		assertThat(post(otherOwner, "/api/v1/salons/" + salonId + "/staff", """
				{"name": "Intruder"}
				""")).hasStatus(HttpStatus.FORBIDDEN);

		long otherSalon = idOf(post(otherOwner, "/api/v1/salons", """
				{"name": "Other Cuts", "opensAt": "09:00", "closesAt": "20:00"}
				"""));
		long otherSalonsStaff = firstStaffId(otherSalon);
		assertThat(freeSlots(otherSalonsStaff)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(book("10:00", otherSalonsStaff)).hasStatus(HttpStatus.NOT_FOUND);
	}

	private MvcTestResult book(String startTime, @Nullable Long staffId) {
		return AuthTestSupport.postJson(mvc, "/api/v1/salons/" + salonId + "/bookings", """
				{"serviceId": %d, "clientName": "Thabo", "clientPhone": "082 123 4567", "date": "%s",
				 "startTime": "%s", "staffId": %s}
				""".formatted(haircutId, TOMORROW, startTime, staffId));
	}

	private MvcTestResult freeSlots(@Nullable Long staffId) {
		String uri = "/api/v1/salons/{id}/free-slots?serviceId={service}&date={date}"
				+ (staffId != null ? "&staffId=" + staffId : "");
		return mvc.get().uri(uri, salonId, haircutId, TOMORROW).exchange();
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
