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
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import io.github.afunemma.salonbooking.AuthTestSupport;
import io.github.afunemma.salonbooking.FixedClockConfiguration;
import io.github.afunemma.salonbooking.TestcontainersConfiguration;

/**
 * The owner's "Services & prices" page: prices typed in rand, stored in cents, and shown
 * to clients straight away.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, FixedClockConfiguration.class })
class OwnerServicesPageIntegrationTest {

	@Autowired
	private MockMvcTester mvc;

	private MockHttpSession session;

	private long salonId;

	@BeforeEach
	void ownerWithSalon() {
		session = registerAndLogIn();
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
		salonId = Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
	}

	@Test
	@DisplayName("An owner adds a service with a price in rand, and clients see it on the booking page")
	void addServiceWithPrice() {
		assertThat(servicesPage()).bodyText().contains("No services yet");

		assertThat(addService("Haircut", "35", "75.50", "")).hasStatus(HttpStatus.FOUND)
			.flash()
			.containsEntry("message", "Service added.");

		assertThat(servicesPage()).bodyText().contains("Haircut", "35 min", "R75.50");
		assertThat(mvc.get().uri("/salons/{id}/book", salonId)).bodyText().contains("Haircut · 35 min · R75.50");
	}

	@Test
	@DisplayName("A price range shows as 'R50–R100'")
	void priceRange() {
		addService("Box braids", "300", "450", "800");

		assertThat(servicesPage()).bodyText().contains("Box braids", "300 min", "R450–R800");
	}

	@Test
	@DisplayName("The edit form shows the current price in rand, and saving changes it")
	void editPrice() {
		addService("Haircut", "35", "75.50", "");
		String html = AuthTestSupport.body(servicesPage());
		String editPath = html.substring(html.indexOf("/owner/salons/" + salonId + "/services/"));
		editPath = editPath.substring(0, editPath.indexOf('"'));

		assertThat(mvc.get().uri(editPath).session(session)).hasStatusOk().bodyText().contains("value=\"75.50\"");
		assertThat(mvc.get().uri(editPath).session(session).header("Accept-Language", "en-ZA"))
			.as("South African browsers: the field still needs a dot, which is what HTML number inputs require")
			.bodyText()
			.contains("value=\"75.50\"");

		assertThat(mvc.post()
			.uri(editPath)
			.session(session)
			.with(csrf())
			.param("name", "Haircut")
			.param("durationMinutes", "40")
			.param("price", "80")
			.param("priceUpTo", "")
			.exchange()).hasStatus(HttpStatus.FOUND)
			.flash()
			.containsEntry("message", "Service updated. Existing bookings keep their times.");
		assertThat(servicesPage()).bodyText().contains("40 min", "R80").doesNotContain("R75.50");
	}

	@Test
	@DisplayName("Too many decimals, a negative price, or a range ending below its start are shown on the form")
	void invalidPrices() {
		assertThat(addService("Haircut", "35", "75.555", "")).hasStatusOk()
			.bodyText()
			.contains("with at most 2 decimals");
		assertThat(addService("Haircut", "35", "-5", "")).hasStatusOk().bodyText().contains("must not be negative");
		assertThat(addService("Haircut", "35", "100", "50")).hasStatusOk()
			.bodyText()
			.contains("must not be lower than it");
		assertThat(addService("Haircut", "35", "", "50")).as("'up to' without a price")
			.hasStatusOk()
			.bodyText()
			.contains("needs a price");
		assertThat(servicesPage()).bodyText().contains("No services yet");
	}

	@Test
	@DisplayName("Another owner can't see or change these services, and forms need the CSRF token")
	void accessAndCsrf() {
		MockHttpSession other = registerAndLogIn();
		assertThat(mvc.get().uri("/owner/salons/{id}/services", salonId).session(other))
			.hasStatus(HttpStatus.NOT_FOUND);

		assertThat(mvc.post()
			.uri("/owner/salons/{id}/services", salonId)
			.session(session)
			.param("name", "Forged")
			.param("durationMinutes", "30")).hasStatus(HttpStatus.FORBIDDEN);
	}

	private MvcTestResult addService(String name, String minutes, String price, String upTo) {
		return mvc.post()
			.uri("/owner/salons/{id}/services", salonId)
			.session(session)
			.with(csrf())
			.param("name", name)
			.param("durationMinutes", minutes)
			.param("price", price)
			.param("priceUpTo", upTo)
			.exchange();
	}

	private MvcTestResult servicesPage() {
		return mvc.get().uri("/owner/salons/{id}/services", salonId).session(session).exchange();
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
