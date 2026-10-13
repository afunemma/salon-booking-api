package io.github.afunemma.salonbooking.demo;

import java.security.SecureRandom;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.afunemma.salonbooking.account.AppUser;
import io.github.afunemma.salonbooking.account.AppUserRepository;
import io.github.afunemma.salonbooking.account.AuthDtos.RegisterRequest;
import io.github.afunemma.salonbooking.account.AuthService;
import io.github.afunemma.salonbooking.salon.SalonDtos.CreateSalonRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.CreateServiceRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.SalonResponse;
import io.github.afunemma.salonbooking.salon.SalonDtos.StaffRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.StaffResponse;
import io.github.afunemma.salonbooking.salon.SalonService;

/**
 * With the {@code demo} profile, creates a sample salon with services and two staff
 * members on first startup, so visitors to the live demo can look up free slots and book
 * without any setup.
 * <p>
 * The demo salon belongs to a demo owner whose password is random and never shown or
 * logged. Visitors who want to try the owner features register their own account. Safe to
 * run on every startup: the salon is created once, and the staff are added once to a demo
 * salon created before staff members existed.
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
		Long ownerId = users.findByEmailIgnoreCase(DEMO_OWNER_EMAIL)
			.map(AppUser::getId)
			.orElseGet(this::createDemoSalon);
		addDemoStaff(ownerId);
	}

	private Long createDemoSalon() {
		Long ownerId = authService.register(new RegisterRequest(DEMO_OWNER_EMAIL, randomPassword())).id();
		SalonResponse salon = salonService
			.createSalon(new CreateSalonRequest("Demo Salon", LocalTime.of(9, 0), LocalTime.of(20, 0)), ownerId);
		salonService.addService(salon.id(), ownerId, new CreateServiceRequest("Haircut", 35, 5000, 10000));
		salonService.addService(salon.id(), ownerId, new CreateServiceRequest("Beard trim", 15, 3000, 5000));
		salonService.addService(salon.id(), ownerId, new CreateServiceRequest("Box braids", 300, 45000, 80000));
		salonService.addService(salon.id(), ownerId, new CreateServiceRequest("Manicure", 60, 20000, 20000));
		log.info("Demo data created: salon={}", salon.id());
		return ownerId;
	}

	/**
	 * Names the first staff member and adds a second who only works afternoons, so
	 * visitors see that each staff member has their own hours. Skipped once the salon has
	 * more than one staff member.
	 */
	private void addDemoStaff(Long ownerId) {
		for (SalonResponse salon : salonService.listOwnedSalons(ownerId)) {
			List<StaffResponse> staff = salonService.listStaff(salon.id());
			if (staff.size() == 1) {
				salonService.updateStaff(salon.id(), staff.getFirst().id(), ownerId,
						new StaffRequest("Sipho", null, null));
				salonService.addStaff(salon.id(), ownerId,
						new StaffRequest("Lindiwe", LocalTime.of(12, 0), LocalTime.of(20, 0)));
				log.info("Demo staff added: salon={}", salon.id());
			}
		}
	}

	/** 32 random bytes: nobody can log in as the demo owner. */
	private static String randomPassword() {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).substring(0, 40);
	}

}
