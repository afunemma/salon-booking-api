package io.github.afunemma.salonbooking.demo;

import java.security.SecureRandom;
import java.time.LocalTime;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.afunemma.salonbooking.account.AppUserRepository;
import io.github.afunemma.salonbooking.account.AuthDtos.RegisterRequest;
import io.github.afunemma.salonbooking.account.AuthService;
import io.github.afunemma.salonbooking.salon.SalonDtos.CreateSalonRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.CreateServiceRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.SalonResponse;
import io.github.afunemma.salonbooking.salon.SalonService;

/**
 * With the {@code demo} profile, creates a sample salon with services on first startup,
 * so visitors to the live demo can look up free slots and book without any setup.
 * <p>
 * The demo salon belongs to a demo owner whose password is random and never shown or
 * logged. Visitors who want to try the owner features register their own account. Runs
 * only once: if the demo owner already exists, nothing happens.
 */
@Component
@Profile("demo")
class DemoDataInitializer implements ApplicationRunner {

	static final String DEMO_OWNER_EMAIL = "demo-owner@salon-booking.invalid";

	private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

	private final AppUserRepository users;

	private final AuthService authService;

	private final SalonService salonService;

	DemoDataInitializer(AppUserRepository users, AuthService authService, SalonService salonService) {
		this.users = users;
		this.authService = authService;
		this.salonService = salonService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (users.existsByEmailIgnoreCase(DEMO_OWNER_EMAIL)) {
			return;
		}
		Long ownerId = authService.register(new RegisterRequest(DEMO_OWNER_EMAIL, randomPassword())).id();
		SalonResponse salon = salonService
			.createSalon(new CreateSalonRequest("Demo Salon", LocalTime.of(9, 0), LocalTime.of(20, 0)), ownerId);
		salonService.addService(salon.id(), ownerId, new CreateServiceRequest("Haircut", 35, 5000, 10000));
		salonService.addService(salon.id(), ownerId, new CreateServiceRequest("Beard trim", 15, 3000, 5000));
		salonService.addService(salon.id(), ownerId, new CreateServiceRequest("Box braids", 300, 45000, 80000));
		salonService.addService(salon.id(), ownerId, new CreateServiceRequest("Manicure", 60, 20000, 20000));
		log.info("Demo data created: salon={}", salon.id());
	}

	/** 32 random bytes: nobody can log in as the demo owner. */
	private static String randomPassword() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, 40);
	}

}
