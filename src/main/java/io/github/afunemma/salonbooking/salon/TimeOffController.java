package io.github.afunemma.salonbooking.salon;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.github.afunemma.salonbooking.common.OpenApiConfig;
import io.github.afunemma.salonbooking.common.TokenService;
import io.github.afunemma.salonbooking.salon.SalonDtos.TimeOffRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.TimeOffResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/salons/{salonId}/time-off")
@Tag(name = "Time off", description = "Staff leave and salon closures (owner only)")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
class TimeOffController {

	private final TimeOffService timeOffService;

	TimeOffController(TimeOffService timeOffService) {
		this.timeOffService = timeOffService;
	}

	@PostMapping
	@Operation(summary = "Add time off for a staff member, or close the whole salon",
			description = "Leave out staffId to close the whole salon. Both days are included. "
					+ "Returns 409 if active bookings fall in the period.")
	ResponseEntity<TimeOffResponse> add(@PathVariable Long salonId, @Valid @RequestBody TimeOffRequest request,
			@AuthenticationPrincipal Jwt token) {
		TimeOffResponse added = timeOffService.add(salonId, TokenService.userIdOf(token), request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
			.path("/{id}")
			.buildAndExpand(added.id())
			.toUri();
		return ResponseEntity.created(location).body(added);
	}

	@GetMapping
	@Operation(summary = "List time off that hasn't ended yet")
	List<TimeOffResponse> listUpcoming(@PathVariable Long salonId, @AuthenticationPrincipal Jwt token) {
		return timeOffService.listUpcoming(salonId, TokenService.userIdOf(token));
	}

	@DeleteMapping("/{timeOffId}")
	@Operation(summary = "Remove time off")
	ResponseEntity<Void> delete(@PathVariable Long salonId, @PathVariable Long timeOffId,
			@AuthenticationPrincipal Jwt token) {
		timeOffService.delete(salonId, timeOffId, TokenService.userIdOf(token));
		return ResponseEntity.noContent().build();
	}

}
