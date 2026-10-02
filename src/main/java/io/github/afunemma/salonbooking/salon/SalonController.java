package io.github.afunemma.salonbooking.salon;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.github.afunemma.salonbooking.common.OpenApiConfig;
import io.github.afunemma.salonbooking.common.TokenService;
import io.github.afunemma.salonbooking.salon.SalonDtos.CreateSalonRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.CreateServiceRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.SalonResponse;
import io.github.afunemma.salonbooking.salon.SalonDtos.ServiceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/salons")
@Tag(name = "Salons", description = "Set up a salon and the services it offers")
class SalonController {

	private final SalonService salonService;

	SalonController(SalonService salonService) {
		this.salonService = salonService;
	}

	@PostMapping
	@Operation(summary = "Create a salon, owned by the logged-in user")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	ResponseEntity<SalonResponse> createSalon(@Valid @RequestBody CreateSalonRequest request,
			@AuthenticationPrincipal Jwt token) {
		SalonResponse salon = salonService.createSalon(request, TokenService.userIdOf(token));
		return ResponseEntity.created(locationOf(salon.id())).body(salon);
	}

	@GetMapping
	@Operation(summary = "List the logged-in user's salons")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	List<SalonResponse> listMySalons(@AuthenticationPrincipal Jwt token) {
		return salonService.listOwnedSalons(TokenService.userIdOf(token));
	}

	@GetMapping("/{salonId}")
	@Operation(summary = "Get a salon")
	SalonResponse getSalon(@PathVariable Long salonId) {
		return salonService.getSalon(salonId);
	}

	@PostMapping("/{salonId}/services")
	@Operation(summary = "Add a service, e.g. a 35-minute haircut (owner only)")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	ResponseEntity<ServiceResponse> addService(@PathVariable Long salonId,
			@Valid @RequestBody CreateServiceRequest request, @AuthenticationPrincipal Jwt token) {
		ServiceResponse service = salonService.addService(salonId, TokenService.userIdOf(token), request);
		return ResponseEntity.created(locationOf(service.id())).body(service);
	}

	@GetMapping("/{salonId}/services")
	@Operation(summary = "List a salon's services")
	List<ServiceResponse> listServices(@PathVariable Long salonId) {
		return salonService.listServices(salonId);
	}

	/**
	 * Builds the new resource's URL from the current request, e.g. POST /api/v1/salons →
	 * /api/v1/salons/42.
	 */
	private static URI locationOf(Long id) {
		return ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri();
	}

}
