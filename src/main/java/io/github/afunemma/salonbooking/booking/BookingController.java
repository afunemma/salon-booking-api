package io.github.afunemma.salonbooking.booking;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.github.afunemma.salonbooking.booking.BookingDtos.BookingResponse;
import io.github.afunemma.salonbooking.booking.BookingDtos.CreateBookingRequest;
import io.github.afunemma.salonbooking.booking.BookingDtos.FreeSlotsResponse;
import io.github.afunemma.salonbooking.booking.BookingDtos.NoShowStatsResponse;
import io.github.afunemma.salonbooking.common.OpenApiConfig;
import io.github.afunemma.salonbooking.common.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/salons/{salonId}")
@Tag(name = "Bookings", description = "Find free times, book them and manage the day")
class BookingController {

	private final BookingService bookingService;

	BookingController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@GetMapping("/free-slots")
	@Operation(summary = "List free start times for a service on a date",
			description = "With staffId: that staff member's free times. Without: times when anyone is free.")
	FreeSlotsResponse freeSlots(@PathVariable Long salonId, @RequestParam Long serviceId, @RequestParam LocalDate date,
			@RequestParam(required = false) @Nullable Long staffId) {
		return bookingService.findFreeSlots(salonId, serviceId, date, staffId);
	}

	@PostMapping("/bookings")
	@Operation(summary = "Book a free slot",
			description = "Without staffId, the first staff member free at that time is booked. Returns 409 Conflict if the slot is no longer free, and 429 Too Many Requests after too many attempts from one IP address.")
	ResponseEntity<BookingResponse> book(@PathVariable Long salonId, @Valid @RequestBody CreateBookingRequest request,
			HttpServletRequest httpRequest) {
		BookingResponse booking = bookingService.book(salonId, request, httpRequest.getRemoteAddr());
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
			.path("/{id}")
			.buildAndExpand(booking.id())
			.toUri();
		return ResponseEntity.created(location).body(booking);
	}

	@GetMapping("/bookings")
	@Operation(summary = "The salon's day view: all bookings on a date (owner only)")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	List<BookingResponse> listBookings(@PathVariable Long salonId, @RequestParam LocalDate date,
			@AuthenticationPrincipal Jwt token) {
		return bookingService.listBookings(salonId, TokenService.userIdOf(token), date);
	}

	@GetMapping("/no-show-stats")
	@Operation(summary = "The salon's no-show rate over a date range (owner only)", description = """
			Rate = no-shows ÷ (completed + no-shows). Cancelled bookings don't count: the client \
			gave notice. Past bookings still marked as booked are counted as `notMarked`, so you know \
			when the rate is incomplete. Ranges can be up to 366 days.""")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	NoShowStatsResponse noShowStats(@PathVariable Long salonId, @RequestParam LocalDate from,
			@RequestParam LocalDate to, @AuthenticationPrincipal Jwt token) {
		return bookingService.noShowStats(salonId, TokenService.userIdOf(token), from, to);
	}

	@PostMapping("/bookings/{bookingId}/cancel")
	@Operation(summary = "Cancel a booking and free its slot (owner only)")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	BookingResponse cancel(@PathVariable Long salonId, @PathVariable Long bookingId,
			@AuthenticationPrincipal Jwt token) {
		return bookingService.cancel(salonId, bookingId, TokenService.userIdOf(token));
	}

	@PostMapping("/bookings/{bookingId}/complete")
	@Operation(summary = "Mark a booking as done (owner only)")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	BookingResponse complete(@PathVariable Long salonId, @PathVariable Long bookingId,
			@AuthenticationPrincipal Jwt token) {
		return bookingService.markCompleted(salonId, bookingId, TokenService.userIdOf(token));
	}

	@PostMapping("/bookings/{bookingId}/no-show")
	@Operation(summary = "Mark that the client didn't arrive (owner only)")
	@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
	BookingResponse noShow(@PathVariable Long salonId, @PathVariable Long bookingId,
			@AuthenticationPrincipal Jwt token) {
		return bookingService.markNoShow(salonId, bookingId, TokenService.userIdOf(token));
	}

}
