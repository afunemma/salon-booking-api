package io.github.afunemma.salonbooking.home;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import io.github.afunemma.salonbooking.TestcontainersConfiguration;

/**
 * The landing page without demo data: no salon to feature, and no demo notice.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HomePageIntegrationTest {

	@Autowired
	private MockMvcTester mvc;

	@Test
	@DisplayName("Anyone can open the landing page, with ways in for clients, owners and developers")
	void landingPage() {
		assertThat(mvc.get().uri("/")).hasStatusOk()
			.bodyText()
			.contains("Salon Booking", "Ask your salon for its booking link.", "href=\"/owner/register\"",
					"href=\"/owner/login\"", "href=\"/swagger-ui.html\"", "github.com/afunemma/salon-booking-api")
			.doesNotContain("This is a demo");
	}

}
