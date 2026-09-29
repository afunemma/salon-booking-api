package io.github.afunemma.salonbooking.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.afunemma.salonbooking.booking.BookingDtos.BookingResponse;
import io.github.afunemma.salonbooking.booking.BookingDtos.CreateBookingRequest;
import io.github.afunemma.salonbooking.booking.BookingDtos.FreeSlotsResponse;
import io.github.afunemma.salonbooking.common.NotFoundException;
import io.github.afunemma.salonbooking.salon.Salon;
import io.github.afunemma.salonbooking.salon.SalonService;
import io.github.afunemma.salonbooking.salon.ServiceOffering;

@Service
@Transactional(readOnly = true)
public class BookingService {

	private final BookingRepository bookings;
	private final SalonService salonService;
	private final Clock clock;
	private final Duration slotStep;

	BookingService(BookingRepository bookings, SalonService salonService, Clock clock,
			@Value("${app.booking.slot-step}") Duration slotStep) {
		this.bookings = bookings;
		this.salonService = salonService;
		this.clock = clock;
		this.slotStep = slotStep;
	}

	public FreeSlotsResponse findFreeSlots(Long salonId, Long serviceId, LocalDate date) {
		ServiceOffering service = salonService.findService(salonId, serviceId);
		List<LocalTime> startTimes = freeStartTimes(service, date);
		return new FreeSlotsResponse(date, serviceId, Math.toIntExact(service.getDuration().toMinutes()), startTimes);
	}

	/**
	 * Books a slot if it is still free.
	 * <p>
	 * Known limitation: two requests for the same slot at the same moment can both
	 * pass the check before either is saved. Preventing that is the next roadmap step.
	 */
	@Transactional
	public BookingResponse book(Long salonId, CreateBookingRequest request) {
		ServiceOffering service = salonService.findService(salonId, request.serviceId());
		if (request.date().isBefore(LocalDate.now(clock))) {
			throw new IllegalArgumentException("Bookings can't be made for a date in the past");
		}
		if (!freeStartTimes(service, request.date()).contains(request.startTime())) {
			throw new SlotUnavailableException(
					request.startTime() + " is not available on " + request.date() + " for " + service.getName());
		}
		Booking booking = new Booking(service, request.clientName().strip(), request.clientPhone().strip(),
				request.date(), request.startTime());
		return BookingResponse.from(bookings.save(booking));
	}

	/** The salon's day view: every booking on a date, including cancelled ones and no-shows. */
	public List<BookingResponse> listBookings(Long salonId, LocalDate date) {
		salonService.findSalon(salonId);
		return bookings.findBySalonIdAndBookingDateOrderByStartTime(salonId, date).stream()
				.map(BookingResponse::from)
				.toList();
	}

	@Transactional
	public BookingResponse cancel(Long salonId, Long bookingId) {
		Booking booking = findBooking(salonId, bookingId);
		booking.cancel();
		return BookingResponse.from(booking);
	}

	@Transactional
	public BookingResponse markCompleted(Long salonId, Long bookingId) {
		Booking booking = findBooking(salonId, bookingId);
		booking.markCompleted();
		return BookingResponse.from(booking);
	}

	@Transactional
	public BookingResponse markNoShow(Long salonId, Long bookingId) {
		Booking booking = findBooking(salonId, bookingId);
		booking.markNoShow();
		return BookingResponse.from(booking);
	}

	private List<LocalTime> freeStartTimes(ServiceOffering service, LocalDate date) {
		LocalDate today = LocalDate.now(clock);
		if (date.isBefore(today)) {
			return List.of();
		}
		Salon salon = service.getSalon();
		List<TimeRange> taken = bookings
				.findBySalonIdAndBookingDateAndStatusOrderByStartTime(salon.getId(), date, BookingStatus.BOOKED)
				.stream()
				.map(Booking::getTimeRange)
				.toList();
		List<LocalTime> free = SlotFinder.findFreeSlots(salon.getOpeningHours(), taken, service.getDuration(),
				slotStep);
		if (date.equals(today)) {
			LocalTime now = LocalTime.now(clock);
			return free.stream().filter(start -> start.isAfter(now)).toList();
		}
		return free;
	}

	private Booking findBooking(Long salonId, Long bookingId) {
		return bookings.findById(bookingId)
				.filter(booking -> booking.getSalon().getId().equals(salonId))
				.orElseThrow(() -> new NotFoundException("Booking " + bookingId + " not found in salon " + salonId));
	}
}
