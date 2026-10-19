package io.github.afunemma.salonbooking.owner;

import java.time.Clock;
import java.time.LocalDate;

import org.jspecify.annotations.Nullable;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import io.github.afunemma.salonbooking.account.AuthDtos.RegisterRequest;
import io.github.afunemma.salonbooking.account.AuthService;
import io.github.afunemma.salonbooking.booking.BookingService;
import io.github.afunemma.salonbooking.common.ConflictException;
import io.github.afunemma.salonbooking.common.NotFoundException;
import io.github.afunemma.salonbooking.salon.SalonDtos.CreateSalonRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.SalonResponse;
import io.github.afunemma.salonbooking.salon.SalonService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;

/**
 * The owner pages: register, log in, see your salons, and manage the day's bookings.
 * Every page goes through the same services as the API, so ownership checks and booking
 * rules apply exactly the same way.
 */
@Controller
@Hidden
class OwnerPagesController {

	private final AuthService authService;

	private final SalonService salonService;

	private final BookingService bookingService;

	private final Clock clock;

	OwnerPagesController(AuthService authService, SalonService salonService, BookingService bookingService,
			Clock clock) {
		this.authService = authService;
		this.salonService = salonService;
		this.bookingService = bookingService;
		this.clock = clock;
	}

	@GetMapping("/owner/login")
	String login() {
		return "owner/login";
	}

	@GetMapping("/owner/register")
	String registerForm(Model model) {
		model.addAttribute("form", new RegisterRequest("", ""));
		return "owner/register";
	}

	@PostMapping("/owner/register")
	String register(@Valid @ModelAttribute("form") RegisterRequest form, BindingResult errors, Model model) {
		if (errors.hasErrors()) {
			return "owner/register";
		}
		try {
			authService.register(form);
		}
		catch (ConflictException ex) {
			model.addAttribute("error", ex.getMessage());
			return "owner/register";
		}
		return "redirect:/owner/login?registered";
	}

	/** "My salons", with a form to create one. */
	@GetMapping("/owner")
	String salons(@AuthenticationPrincipal OwnerPrincipal owner, Model model) {
		model.addAttribute("owner", owner);
		model.addAttribute("salons", salonService.listOwnedSalons(owner.userId()));
		if (!model.containsAttribute("form")) {
			model.addAttribute("form", new CreateSalonRequest("", null, null));
		}
		return "owner/salons";
	}

	@PostMapping("/owner/salons")
	String createSalon(@AuthenticationPrincipal OwnerPrincipal owner,
			@Valid @ModelAttribute("form") CreateSalonRequest form, BindingResult errors, Model model) {
		if (errors.hasErrors()) {
			return salons(owner, model);
		}
		SalonResponse salon = salonService.createSalon(form, owner.userId());
		return "redirect:/owner/salons/" + salon.id();
	}

	/** The day view: every booking on a date, with buttons to update them. */
	@GetMapping("/owner/salons/{salonId}")
	String day(@AuthenticationPrincipal OwnerPrincipal owner, @PathVariable Long salonId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate date,
			Model model) {
		LocalDate day = date != null ? date : LocalDate.now(clock);
		model.addAttribute("salon", salonService.findOwnedSalon(salonId, owner.userId()).getName());
		model.addAttribute("salonId", salonId);
		model.addAttribute("date", day);
		model.addAttribute("bookings", bookingService.listBookings(salonId, owner.userId(), day));
		return "owner/day";
	}

	/** Marks a booking done, a no-show, or cancelled, then shows the day again. */
	@PostMapping("/owner/salons/{salonId}/bookings/{bookingId}/{action}")
	String updateBooking(@AuthenticationPrincipal OwnerPrincipal owner, @PathVariable Long salonId,
			@PathVariable Long bookingId, @PathVariable String action,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, RedirectAttributes redirect) {
		try {
			switch (action) {
				case "complete" -> bookingService.markCompleted(salonId, bookingId, owner.userId());
				case "no-show" -> bookingService.markNoShow(salonId, bookingId, owner.userId());
				case "cancel" -> bookingService.cancel(salonId, bookingId, owner.userId());
				default -> throw new NotFoundException("Unknown action " + action);
			}
			redirect.addFlashAttribute("message", "Booking updated.");
		}
		catch (ConflictException | OptimisticLockingFailureException ex) {
			redirect.addFlashAttribute("error", "That booking was changed in the meantime. Here is how it stands now.");
		}
		return "redirect:/owner/salons/" + salonId + "?date=" + date;
	}

	/**
	 * Another owner's salon looks the same as one that doesn't exist, so the pages can't
	 * be used to discover other salons.
	 */
	@ExceptionHandler({ NotFoundException.class, AccessDeniedException.class })
	@ResponseStatus(HttpStatus.NOT_FOUND)
	String notFound() {
		return "owner/not-found";
	}

}
