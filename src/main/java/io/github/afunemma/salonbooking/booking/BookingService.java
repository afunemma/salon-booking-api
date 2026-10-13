package io.github.afunemma.salonbooking.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.afunemma.salonbooking.booking.BookingDtos.BookingResponse;
import io.github.afunemma.salonbooking.booking.BookingDtos.CreateBookingRequest;
import io.github.afunemma.salonbooking.booking.BookingDtos.FreeSlotsResponse;
import io.github.afunemma.salonbooking.booking.BookingDtos.NoShowStatsResponse;
import io.github.afunemma.salonbooking.common.AppProperties;
import io.github.afunemma.salonbooking.common.NotFoundException;
import io.github.afunemma.salonbooking.salon.SalonService;
import io.github.afunemma.salonbooking.salon.ServiceOffering;
import io.github.afunemma.salonbooking.salon.StaffMember;
import io.github.afunemma.salonbooking.salon.TimeOff;
import io.github.afunemma.salonbooking.salon.TimeOffService;
import io.github.afunemma.salonbooking.scheduling.SlotFinder;
import io.github.afunemma.salonbooking.scheduling.TimeRange;
import io.github.afunemma.salonbooking.scheduling.WorkSchedule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
@Transactional(readOnly = true)
public class BookingService {

	private static final Logger log = LoggerFactory.getLogger(BookingService.class);

	/**
	 * Name of the database constraint that rejects overlapping bookings (see V2
	 * migration).
	 */
	private static final String OVERLAP_CONSTRAINT = "booking_no_overlap";

	/** PostgreSQL's error code for a broken exclusion constraint. */
	private static final String EXCLUSION_VIOLATION = "23P01";

	private final BookingRepository bookings;

	private final SalonService salonService;

	private final TimeOffService timeOffService;

	private final Clock clock;

	private final Duration slotStep;

	private final MeterRegistry meters;

	private final Counter bookingsMade;

	private final CancelLinks cancelLinks;

	/**
	 * Longest range for no-show statistics: a year, so one request can't make the
	 * database count years of bookings.
	 */
	private static final int MAX_STATS_DAYS = 366;

	BookingService(BookingRepository bookings, SalonService salonService, TimeOffService timeOffService, Clock clock,
			AppProperties properties, MeterRegistry meters, CancelLinks cancelLinks) {
		this.bookings = bookings;
		this.salonService = salonService;
		this.timeOffService = timeOffService;
		this.clock = clock;
		this.cancelLinks = cancelLinks;
		this.slotStep = properties.booking().slotStep();
		this.meters = meters;
		this.bookingsMade = Counter.builder("salon.bookings.made")
			.description("Bookings made by clients")
			.register(meters);
	}

	/**
	 * With a staff member: their free start times. Without: every time at which at least
	 * one staff member is free.
	 */
	public FreeSlotsResponse findFreeSlots(Long salonId, Long serviceId, LocalDate date, @Nullable Long staffId) {
		ServiceOffering service = salonService.findService(salonId, serviceId);
		SortedSet<LocalTime> startTimes = new TreeSet<>();
		freeTimesByStaff(service, staffToConsider(salonId, staffId), date).values().forEach(startTimes::addAll);
		return new FreeSlotsResponse(date, serviceId, staffId, Math.toIntExact(service.getDuration().toMinutes()),
				List.copyOf(startTimes));
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
			countRejected("date_in_past");
			throw new BookingNotAllowedException("Bookings can't be made for a date in the past");
		}
		// The first staff member (in the order they were added) who is free then.
		StaffMember staff = freeTimesByStaff(service, staffToConsider(salonId, request.staffId()), request.date())
			.entrySet()
			.stream()
			.filter(entry -> entry.getValue().contains(request.startTime()))
			.map(Map.Entry::getKey)
			.findFirst()
			.orElseThrow(() -> {
				countRejected("slot_unavailable");
				return new SlotUnavailableException(
						request.startTime() + " is not available on " + request.date() + " for " + service.getName());
			});
		Booking booking = new Booking(service, staff, request.clientName().strip(), request.clientPhone().strip(),
				request.date(), request.startTime());
		try {
			bookings.saveAndFlush(booking);
		}
		catch (DataIntegrityViolationException ex) {
			if (isOverlapViolation(ex)) {
				// Should be rare: the salon lock normally stops overlaps before they
				// reach the database.
				countRejected("slot_taken_concurrently");
				log.warn("Overlapping booking rejected by database: salon={} date={} start={}", salonId, request.date(),
						request.startTime());
				throw new SlotUnavailableException(
						request.startTime() + " on " + request.date() + " was just booked by someone else");
			}
			throw ex;
		}
		// Client name and phone number are personal information (POPIA), so they are not
		// logged.
		bookingsMade.increment();
		log.info("Booking created: id={} salon={} service={} staff={} date={} start={}", booking.getId(), salonId,
				service.getId(), staff.getId(), booking.getBookingDate(), booking.getStartTime());
		return BookingResponse.withCancelUrl(booking, cancelLinks.urlFor(booking.getId()));
	}

	/**
	 * The salon's day view: every booking on a date, including cancelled ones and
	 * no-shows. Owner only.
	 */
	public List<BookingResponse> listBookings(Long salonId, Long userId, LocalDate date) {
		salonService.findOwnedSalon(salonId, userId);
		return bookings.findBySalonIdAndBookingDateOrderByStartTime(salonId, date)
			.stream()
			.map(BookingResponse::from)
			.toList();
	}

	/**
	 * The salon's no-show rate over a date range (owner only), e.g. to see whether
	 * reminders reduce no-shows.
	 */
	public NoShowStatsResponse noShowStats(Long salonId, Long userId, LocalDate from, LocalDate to) {
		salonService.findOwnedSalon(salonId, userId);
		if (to.isBefore(from)) {
			throw new InvalidDateRangeException("'to' (" + to + ") is before 'from' (" + from + ")");
		}
		if (ChronoUnit.DAYS.between(from, to) >= MAX_STATS_DAYS) {
			throw new InvalidDateRangeException("A range can be at most " + MAX_STATS_DAYS + " days");
		}
		Map<BookingStatus, Long> counts = new EnumMap<>(BookingStatus.class);
		bookings.countByStatus(salonId, from, to).forEach(row -> counts.put(row.getStatus(), row.getCount()));
		return NoShowStatsResponse.of(from, to, counts.getOrDefault(BookingStatus.COMPLETED, 0L),
				counts.getOrDefault(BookingStatus.NO_SHOW, 0L), counts.getOrDefault(BookingStatus.CANCELLED, 0L),
				countNotMarked(salonId, from, to));
	}

	/**
	 * Bookings before today that are still {@code BOOKED}. Today's and future bookings
	 * can't have an outcome yet, so they don't count.
	 */
	private long countNotMarked(Long salonId, LocalDate from, LocalDate to) {
		LocalDate yesterday = LocalDate.now(clock).minusDays(1);
		if (from.isAfter(yesterday)) {
			return 0;
		}
		LocalDate end = to.isBefore(yesterday) ? to : yesterday;
		return bookings.countBySalonIdAndStatusAndBookingDateBetween(salonId, BookingStatus.BOOKED, from, end);
	}

	@Transactional
	public BookingResponse cancel(Long salonId, Long bookingId, Long userId) {
		Booking booking = findOwnedBooking(salonId, bookingId, userId);
		booking.cancel();
		countStatusChange(booking);
		log.info("Booking cancelled: id={} salon={}", bookingId, salonId);
		return BookingResponse.from(booking);
	}

	@Transactional
	public BookingResponse markCompleted(Long salonId, Long bookingId, Long userId) {
		Booking booking = findOwnedBooking(salonId, bookingId, userId);
		booking.markCompleted();
		countStatusChange(booking);
		log.info("Booking completed: id={} salon={}", bookingId, salonId);
		return BookingResponse.from(booking);
	}

	@Transactional
	public BookingResponse markNoShow(Long salonId, Long bookingId, Long userId) {
		Booking booking = findOwnedBooking(salonId, bookingId, userId);
		booking.markNoShow();
		countStatusChange(booking);
		log.info("Booking marked as no-show: id={} salon={}", bookingId, salonId);
		return BookingResponse.from(booking);
	}

	/**
	 * The booking behind a client's cancel link, or empty if the link is wrong. A wrong
	 * token and an unknown booking look the same, so links can't be used to discover
	 * which bookings exist.
	 */
	public Optional<ClientBookingView> findForClient(Long bookingId, String token) {
		if (!cancelLinks.isValid(bookingId, token)) {
			return Optional.empty();
		}
		return bookings.findById(bookingId).map(booking -> clientView(booking, token));
	}

	/**
	 * Cancels a booking from the client's link, if it is still active and in the future.
	 * Cancelling twice is harmless: the second time nothing changes.
	 */
	@Transactional
	public Optional<ClientBookingView> cancelByClient(Long bookingId, String token) {
		if (!cancelLinks.isValid(bookingId, token)) {
			return Optional.empty();
		}
		return bookings.findById(bookingId).map(booking -> {
			if (clientState(booking) == ClientBookingView.State.CAN_CANCEL) {
				booking.cancel();
				countStatusChange(booking);
				log.info("Booking cancelled by client: id={} salon={}", bookingId, booking.getSalon().getId());
			}
			return clientView(booking, token);
		});
	}

	private ClientBookingView clientView(Booking booking, String token) {
		return new ClientBookingView(booking.getId(), token, booking.getClientName(), booking.getService().getName(),
				booking.getSalon().getName(), booking.getBookingDate(), booking.getStartTime(), clientState(booking));
	}

	private ClientBookingView.State clientState(Booking booking) {
		return switch (booking.getStatus()) {
			case CANCELLED -> ClientBookingView.State.CANCELLED;
			case COMPLETED, NO_SHOW -> ClientBookingView.State.TOO_LATE;
			case BOOKED -> booking.getBookingDate().atTime(booking.getStartTime()).isAfter(LocalDateTime.now(clock))
					? ClientBookingView.State.CAN_CANCEL : ClientBookingView.State.TOO_LATE;
		};
	}

	private void countRejected(String reason) {
		meters.counter("salon.bookings.rejected", "reason", reason).increment();
	}

	/**
	 * Cancellations, completions and no-shows, e.g. to track the no-show rate over time.
	 */
	private void countStatusChange(Booking booking) {
		meters.counter("salon.bookings.status.changed", "status", booking.getStatus().name().toLowerCase(Locale.ROOT))
			.increment();
	}

	private List<StaffMember> staffToConsider(Long salonId, @Nullable Long staffId) {
		return staffId != null ? List.of(salonService.findStaff(salonId, staffId)) : salonService.staffOf(salonId);
	}

	/**
	 * Each staff member's free start times on a date, in the same order as {@code staff}:
	 * none on their days off or time off, and never during their break. The day's
	 * bookings are loaded in one query and then split by staff member.
	 */
	private Map<StaffMember, List<LocalTime>> freeTimesByStaff(ServiceOffering service, List<StaffMember> staff,
			LocalDate date) {
		Map<StaffMember, List<LocalTime>> result = new LinkedHashMap<>();
		LocalDate today = LocalDate.now(clock);
		if (date.isBefore(today)) {
			return result;
		}
		Map<Long, List<TimeRange>> takenByStaff = bookings
			.findBySalonIdAndBookingDateAndStatusOrderByStartTime(service.getSalon().getId(), date,
					BookingStatus.BOOKED)
			.stream()
			.collect(Collectors.groupingBy(booking -> booking.getStaff().getId(),
					Collectors.mapping(Booking::getTimeRange, Collectors.toList())));
		List<TimeOff> timeOff = timeOffService.onDate(service.getSalon().getId(), date);
		for (StaffMember member : staff) {
			WorkSchedule schedule = member.getSchedule();
			if (!schedule.worksOn(date.getDayOfWeek()) || timeOff.stream().anyMatch(off -> off.appliesTo(member))) {
				result.put(member, List.of());
				continue;
			}
			// A break is treated like a booking that is always there.
			List<TimeRange> taken = new ArrayList<>(takenByStaff.getOrDefault(member.getId(), List.of()));
			taken.addAll(schedule.breaks());
			List<LocalTime> free = SlotFinder.findFreeSlots(schedule.hours(), taken, service.getDuration(), slotStep);
			if (date.equals(today)) {
				LocalTime now = LocalTime.now(clock);
				free = free.stream().filter(start -> start.isAfter(now)).toList();
			}
			result.put(member, free);
		}
		return result;
	}

	/**
	 * Recognises the overlap error by its SQL error code and constraint name, which are
	 * stable, rather than by its message text, which can change between versions. The
	 * name comes from PostgreSQL's own error details, because Hibernate doesn't extract
	 * it for exclusion constraints.
	 */
	private static boolean isOverlapViolation(DataIntegrityViolationException ex) {
		for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
			if (cause instanceof PSQLException psql
					&& psql.getServerErrorMessage() instanceof ServerErrorMessage error) {
				return EXCLUSION_VIOLATION.equals(psql.getSQLState())
						&& OVERLAP_CONSTRAINT.equals(error.getConstraint());
			}
		}
		return false;
	}

	private Booking findOwnedBooking(Long salonId, Long bookingId, Long userId) {
		salonService.findOwnedSalon(salonId, userId);
		return bookings.findById(bookingId)
			.filter(booking -> salonId.equals(booking.getSalon().getId()))
			.orElseThrow(() -> new NotFoundException("Booking " + bookingId + " not found in salon " + salonId));
	}

}
