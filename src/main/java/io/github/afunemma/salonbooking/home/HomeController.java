package io.github.afunemma.salonbooking.home;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import io.github.afunemma.salonbooking.salon.SalonService;
import io.swagger.v3.oas.annotations.Hidden;

/**
 * The landing page: a short pitch and three ways in, for clients, salon owners and
 * developers.
 */
@Controller
@Hidden
class HomeController {

	private final ObjectProvider<FeaturedSalon> featuredSalon;

	private final SalonService salonService;

	HomeController(ObjectProvider<FeaturedSalon> featuredSalon, SalonService salonService) {
		this.featuredSalon = featuredSalon;
		this.salonService = salonService;
	}

	@GetMapping("/")
	String home(Model model) {
		FeaturedSalon featured = featuredSalon.getIfAvailable();
		Optional<Long> salonId = featured != null ? featured.salonId() : Optional.empty();
		model.addAttribute("demo", featured != null);
		salonId.ifPresent(id -> {
			model.addAttribute("featuredSalonId", id);
			model.addAttribute("featuredSalonName", salonService.getSalon(id).name());
		});
		return "index";
	}

}
