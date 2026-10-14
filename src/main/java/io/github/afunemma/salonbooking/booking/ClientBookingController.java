package io.github.afunemma.salonbooking.booking;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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

import io.github.afunemma.salonbooking.booking.BookingDtos.BookingResponse;
import io.github.afunemma.salonbooking.booking.BookingDtos.CreateBookingRequest;
import io.github.afunemma.salonbooking.common.ConflictException;
import io.github.afunemma.salonbooking.common.BusinessRuleException;
import io.github.afunemma.salonbooking.common.NotFoundException;
import io.github.afunemma.salonbooking.salon.SalonDtos.SalonResponse;
import io.github.afunemma.salonbooking.salon.SalonDtos.ServiceResponse;
import io.github.afunemma.salonbooking.salon.SalonDtos.StaffResponse;
import io.github.afunemma.salonbooking.salon.SalonService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;

/**
 * The web pages clients use to book: pick a service, day and time, then give a name and
 * phone number. Plain HTML forms, no JavaScript, so it works on any phone.
 * <p>
 * Booking goes through the same {@link BookingService#book} as the JSON API, so every
 * rule (double-booking protection, staff hours, breaks, time off) applies exactly the
 * same way. Afterwards the client lands on their own booking page, the one their cancel
 * link opens.
 */
@Controller
@Hidden
class ClientBookingController {

	/** How far ahead the date picker goes. Clients usually book a day or two ahead. */
	private static final int DAYS_AHEAD = 30;

	private final BookingService bookingService;

	private final SalonService salonService;

	private final Clock clock;

	ClientBookingController(BookingService bookingService, SalonService salonService, Clock clock) {
		this.bookingService = bookingService;
		this.salonService = salonService;
		this.clock = clock;
	}

	/**
	 * Step 1: choose a service, a day and (optionally) who to book with; see free times.
	 */
	@GetMapping("/salons/{salonId}/book")
	String chooseTime(@PathVariable Long salonId, @RequestParam(required = false) @Nullable Long serviceId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) @Nullable LocalDate date,
			@RequestParam(required = false) @Nullable Long staffId, Model model) {
		SalonResponse salon = salonService.getSalon(salonId);
		List<ServiceResponse> services = salonService.listServices(salonId);
		LocalDate today = LocalDate.now(clock);
		LocalDate chosenDate = date != null ? date : today.plusDays(1);
		Long chosenService = serviceId != null ? serviceId
				: services.stream().map(ServiceResponse::id).findFirst().orElse(null);
		model.addAttribute("salon", salon);
		model.addAttribute("services", services.stream().map(ServiceOption::from).toList());
		model.addAttribute("staff", salonService.listStaff(salonId));
		model.addAttribute("serviceId", chosenService);
		model.addAttribute("date", chosenDate);
		model.addAttribute("staffId", staffId);
		model.addAttribute("minDate", today);
		model.addAttribute("maxDate", today.plusDays(DAYS_AHEAD));
		if (chosenService != null) {
			model.addAttribute("startTimes",
					bookingService.findFreeSlots(salonId, chosenService, chosenDate, staffId).startTimes());
		}
		return "book";
	}

	/** Step 2: the chosen time, and a form for the client's name and phone number. */
	@GetMapping("/salons/{salonId}/book/details")
	String enterDetails(@PathVariable Long salonId, @RequestParam Long serviceId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime startTime,
			@RequestParam(required = false) @Nullable Long staffId, Model model) {
		model.addAttribute("form", new CreateBookingRequest(serviceId, "", "", date, startTime, staffId));
		return details(salonId, serviceId, staffId, model);
	}

	/**
	 * Step 3: book. On success, redirect (Post/Redirect/Get) to the client's own booking
	 * page; on a mistake or a slot just taken, show the form again with the message.
	 */
	@PostMapping("/salons/{salonId}/book")
	String book(@PathVariable Long salonId, @Valid @ModelAttribute("form") CreateBookingRequest form,
			BindingResult errors, Model model) {
		if (errors.hasErrors()) {
			return details(salonId, form.serviceId(), form.staffId(), model);
		}
		try {
			BookingResponse booking = bookingService.book(salonId, form);
			return "redirect:" + booking.cancelUrl() + "&booked=true";
		}
		catch (ConflictException | BusinessRuleException ex) {
			model.addAttribute("bookingError", ex.getMessage());
			return details(salonId, form.serviceId(), form.staffId(), model);
		}
	}

	/** An unknown salon, service or staff member gets a page, not a JSON error. */
	@ExceptionHandler(NotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	String notFound() {
		return "book-not-found";
	}

	private String details(Long salonId, @Nullable Long serviceId, @Nullable Long staffId, Model model) {
		model.addAttribute("salon", salonService.getSalon(salonId));
		if (serviceId != null) {
			ServiceResponse service = salonService.listServices(salonId)
				.stream()
				.filter(candidate -> candidate.id().equals(serviceId))
				.findFirst()
				.orElseThrow(() -> new NotFoundException("Service " + serviceId + " not found in salon " + salonId));
			model.addAttribute("service", ServiceOption.from(service));
		}
		if (staffId != null) {
			model.addAttribute("staffName", salonService.listStaff(salonId)
				.stream()
				.filter(member -> member.id().equals(staffId))
				.map(StaffResponse::name)
				.findFirst()
				.orElseThrow(
						() -> new NotFoundException("Staff member " + staffId + " not found in salon " + salonId)));
		}
		return "book-details";
	}

	/** A service as the client sees it, e.g. "Haircut · 35 min · R50–R100". */
	record ServiceOption(Long id, String label) {

		static ServiceOption from(ServiceResponse service) {
			StringBuilder label = new StringBuilder(service.name()).append(" · ")
				.append(service.durationMinutes())
				.append(" min");
			Integer from = service.priceFromCents();
			Integer to = service.priceToCents();
			if (from != null && to != null && !from.equals(to)) {
				label.append(" · R").append(from / 100).append("–R").append(to / 100);
			}
			else if (from != null) {
				label.append(" · R").append(from / 100);
			}
			return new ServiceOption(service.id(), label.toString());
		}
	}

}
