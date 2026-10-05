package io.github.afunemma.salonbooking.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import io.github.afunemma.salonbooking.TestcontainersConfiguration;
import io.github.afunemma.salonbooking.account.AppUserRepository;
import io.github.afunemma.salonbooking.salon.SalonDtos.ServiceResponse;
import io.github.afunemma.salonbooking.salon.SalonService;

@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class DemoDataInitializerTest {

	@Autowired
	private DemoDataInitializer initializer;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private SalonService salonService;

	@Test
	@DisplayName("The demo profile creates one demo salon with services, and running it again changes nothing")
	void createsDemoSalonOnce() {
		// The initializer already ran at startup. Running it again must not create
		// duplicates.
		initializer.run(new DefaultApplicationArguments());

		Long ownerId = users.findByEmailIgnoreCase(DemoDataInitializer.DEMO_OWNER_EMAIL).orElseThrow().getId();
		var salons = salonService.listOwnedSalons(ownerId);
		assertThat(salons).singleElement().satisfies(salon -> {
			assertThat(salon.name()).isEqualTo("Demo Salon");
			assertThat(salonService.listServices(salon.id())).extracting(ServiceResponse::name)
				.containsExactlyInAnyOrder("Haircut", "Beard trim", "Box braids", "Manicure");
		});
	}

}
