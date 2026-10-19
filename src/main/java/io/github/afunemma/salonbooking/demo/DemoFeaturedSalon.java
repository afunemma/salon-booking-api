package io.github.afunemma.salonbooking.demo;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import io.github.afunemma.salonbooking.account.AppUser;
import io.github.afunemma.salonbooking.account.AppUserRepository;
import io.github.afunemma.salonbooking.home.FeaturedSalon;
import io.github.afunemma.salonbooking.salon.SalonDtos.SalonResponse;
import io.github.afunemma.salonbooking.salon.SalonService;

/**
 * On the live demo, the landing page links to the Demo Salon, so visitors can try booking
 * without any set-up.
 */
@Component
@Profile("demo")
class DemoFeaturedSalon implements FeaturedSalon {

	private final AppUserRepository users;

	private final SalonService salonService;

	DemoFeaturedSalon(AppUserRepository users, SalonService salonService) {
		this.users = users;
		this.salonService = salonService;
	}

	@Override
	public Optional<Long> salonId() {
		return users.findByEmailIgnoreCase(DemoDataInitializer.DEMO_OWNER_EMAIL)
			.map(AppUser::getId)
			.flatMap(ownerId -> salonService.listOwnedSalons(ownerId).stream().findFirst())
			.map(SalonResponse::id);
	}

}
