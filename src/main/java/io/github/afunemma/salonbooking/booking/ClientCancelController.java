package io.github.afunemma.salonbooking.booking;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriComponentsBuilder;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletResponse;

/**
 * The web page behind the cancel link in a client's reminder. These are HTML pages for a
 * phone browser, not JSON API endpoints, so they live outside {@code /api/v1}.
 * <p>
 * Opening the link (GET) never changes anything: chat apps such as WhatsApp open links in
 * the background to show a preview, and that must not cancel a booking. The client
 * cancels by pressing the button, which sends a POST and then redirects back to the page
 * (Post/Redirect/Get), so refreshing the page doesn't send the form again.
 */
@Controller
@Hidden
class ClientCancelController {

	private static final String PAGE = "cancel-booking";

	private static final String INVALID_LINK_PAGE = "cancel-link-invalid";

	private final BookingService bookingService;

	ClientCancelController(BookingService bookingService) {
		this.bookingService = bookingService;
	}

	@GetMapping("/bookings/{bookingId}/cancel")
	String show(@PathVariable Long bookingId, @RequestParam String token,
			@RequestParam(defaultValue = "false") boolean booked, Model model, HttpServletResponse response) {
		return bookingService.findForClient(bookingId, token).map(booking -> {
			model.addAttribute("booking", booking);
			// Just booked through the booking page: say so above the booking.
			model.addAttribute("booked", booked);
			return PAGE;
		}).orElseGet(() -> invalidLink(response));
	}

	@PostMapping("/bookings/{bookingId}/cancel")
	String cancel(@PathVariable Long bookingId, @RequestParam String token, HttpServletResponse response) {
		try {
			return bookingService.cancelByClient(bookingId, token)
				.map(booking -> redirectToPage(bookingId, token))
				.orElseGet(() -> invalidLink(response));
		}
		catch (OptimisticLockingFailureException ex) {
			// The salon changed the booking at the same moment (e.g. marked it done).
			// Showing the page again shows what it is now.
			return redirectToPage(bookingId, token);
		}
	}

	private static String redirectToPage(Long bookingId, String token) {
		return "redirect:" + UriComponentsBuilder.fromPath("/bookings/{id}/cancel")
			.queryParam("token", token)
			.buildAndExpand(bookingId)
			.encode()
			.toUriString();
	}

	private static String invalidLink(HttpServletResponse response) {
		response.setStatus(HttpStatus.NOT_FOUND.value());
		return INVALID_LINK_PAGE;
	}

}
