package io.github.afunemma.salonbooking.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import io.github.afunemma.salonbooking.TestcontainersConfiguration;
import io.github.afunemma.salonbooking.account.AppUserRepository;
import io.github.afunemma.salonbooking.salon.SalonService;

/**
 * On the live demo, the landing page links straight to the Demo Salon's booking page.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class DemoHomePageTest {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private SalonService salonService;

	@Test
	@DisplayName("The landing page features the Demo Salon and says it's a demo")
	void featuresDemoSalon() {
		Long ownerId = users.findByEmailIgnoreCase(DemoDataInitializer.DEMO_OWNER_EMAIL).orElseThrow().getId();
		Long demoSalonId = salonService.listOwnedSalons(ownerId).getFirst().id();

		assertThat(mvc.get().uri("/")).hasStatusOk()
			.bodyText()
			.contains("Book at Demo Salon", "href=\"/salons/" + demoSalonId + "/book\"", "This is a demo")
			.doesNotContain("Ask your salon for its booking link.");
	}

}
