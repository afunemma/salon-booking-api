package io.github.afunemma.salonbooking;

import static io.github.afunemma.salonbooking.AuthTestSupport.postJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Health checks are public, business metrics are counted, and the metrics endpoint is not
 * reachable on the public port.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class MonitoringIntegrationTest {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private MeterRegistry meters;

	@Test
	@DisplayName("Liveness and readiness checks are public on the main port")
	void healthProbesArePublic() {
		assertThat(mvc.get().uri("/livez")).hasStatusOk();
		assertThat(mvc.get().uri("/readyz")).hasStatusOk();
	}

	@Test
	@DisplayName("Metrics are not served on the public port (they live on the internal management port)")
	void metricsAreNotOnPublicPort() {
		assertThat(mvc.get().uri("/actuator/prometheus")).hasStatus(HttpStatus.UNAUTHORIZED);
		String owner = AuthTestSupport.registerAndLogin(mvc);
		assertThat(mvc.get().uri("/actuator/prometheus").header(AUTHORIZATION, owner)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	@DisplayName("Bookings, rejections and no-shows are counted")
	void businessEventsAreCounted() {
		double madeBefore = count("salon.bookings.made");
		double rejectedBefore = count("salon.bookings.rejected", "reason", "slot_unavailable");
		double noShowsBefore = count("salon.bookings.status.changed", "status", "no_show");

		String owner = AuthTestSupport.registerAndLogin(mvc);
		long salonId = idOf(post(owner, "/api/v1/salons", """
				{"name": "Metrics Cuts", "opensAt": "09:00", "closesAt": "17:00"}
				"""));
		long serviceId = idOf(post(owner, "/api/v1/salons/" + salonId + "/services", """
				{"name": "Haircut", "durationMinutes": 35}
				"""));
		String booking = """
				{"serviceId": %d, "clientName": "Thabo", "clientPhone": "082 123 4567",
				 "date": "2030-01-07", "startTime": "10:00"}
				""".formatted(serviceId);
		long bookingId = idOf(postJson(mvc, "/api/v1/salons/" + salonId + "/bookings", booking));
		assertThat(postJson(mvc, "/api/v1/salons/" + salonId + "/bookings", booking)).hasStatus(HttpStatus.CONFLICT);
		assertThat(mvc.post()
			.uri("/api/v1/salons/{salon}/bookings/{booking}/no-show", salonId, bookingId)
			.header(AUTHORIZATION, owner)).hasStatusOk();

		assertThat(count("salon.bookings.made")).isEqualTo(madeBefore + 1);
		assertThat(count("salon.bookings.rejected", "reason", "slot_unavailable")).isEqualTo(rejectedBefore + 1);
		assertThat(count("salon.bookings.status.changed", "status", "no_show")).isEqualTo(noShowsBefore + 1);
	}

	@Test
	@DisplayName("Successful and failed logins are counted")
	void loginsAreCounted() {
		double failuresBefore = count("salon.auth.logins", "result", "failure");

		postJson(mvc, "/api/v1/auth/login", """
				{"email": "%s", "password": "wrong-password"}
				""".formatted(AuthTestSupport.uniqueEmail()));

		assertThat(count("salon.auth.logins", "result", "failure")).isEqualTo(failuresBefore + 1);
	}

	/** Current value of a counter, or 0 if it hasn't been created yet. */
	private double count(String name, String... tags) {
		Counter counter = meters.find(name).tags(tags).counter();
		return (counter != null) ? counter.count() : 0;
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
