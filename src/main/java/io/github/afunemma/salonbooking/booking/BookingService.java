package io.github.afunemma.salonbooking.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
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

	/** Name of the database constraint that rejects overlapping bookings (see V2 migration). */
	private static final String OVERLAP_CONSTRAINT = "booking_no_overlap";

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
	 * Checking "is it free?" and then saving has a race: two requests can both pass the
	 * check before either saves. Two layers stop that:
	 * <ol>
	 * <li>The salon's row is locked first, so bookings for one salon run one at a time
	 * and the second request sees the first one's booking.</li>
	 * <li>The database's {@code booking_no_overlap} constraint rejects overlaps even if
	 * some other code path forgets the lock.</li>
	 * </ol>
	 */
	@Transactional
	public BookingResponse book(Long salonId, CreateBookingRequest request) {
		salonService.lockSalon(salonId);
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
		try {
			return BookingResponse.from(bookings.saveAndFlush(booking));
		}
		catch (DataIntegrityViolationException ex) {
			if (isOverlapViolation(ex)) {
				throw new SlotUnavailableException(request.startTime() + " on " + request.date()
						+ " was just booked by someone else");
			}
			throw ex;
		}
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

	private static boolean isOverlapViolation(DataIntegrityViolationException ex) {
		return ex.getMostSpecificCause().getMessage().contains(OVERLAP_CONSTRAINT);
	}

	private Booking findBooking(Long salonId, Long bookingId) {
		return bookings.findById(bookingId)
				.filter(booking -> booking.getSalon().getId().equals(salonId))
				.orElseThrow(() -> new NotFoundException("Booking " + bookingId + " not found in salon " + salonId));
	}
}
