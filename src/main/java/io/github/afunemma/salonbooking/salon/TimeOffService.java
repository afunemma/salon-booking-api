package io.github.afunemma.salonbooking.salon;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.afunemma.salonbooking.common.NotFoundException;
import io.github.afunemma.salonbooking.salon.ActiveBookings.BookedTime;
import io.github.afunemma.salonbooking.salon.SalonDtos.TimeOffRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.TimeOffResponse;

/**
 * Dated time off: a staff member's leave, or the whole salon closed.
 */
@Service
@Transactional(readOnly = true)
public class TimeOffService {

	private static final Logger log = LoggerFactory.getLogger(TimeOffService.class);

	/** Longest time off in one go: a year. */
	private static final int MAX_DAYS = 366;

	private final TimeOffRepository timeOff;

	private final SalonService salonService;

	private final ActiveBookings activeBookings;

	private final Clock clock;

	TimeOffService(TimeOffRepository timeOff, SalonService salonService, ActiveBookings activeBookings, Clock clock) {
		this.timeOff = timeOff;
		this.salonService = salonService;
		this.activeBookings = activeBookings;
		this.clock = clock;
	}

	/**
	 * Adds time off, unless active bookings fall in it: those clients would arrive to
	 * find nobody there. The owner cancels those bookings first.
	 * <p>
	 * The salon is locked first, like a booking is, so a client can't book a slot in this
	 * period between the check and the save.
	 */
	@Transactional
	public TimeOffResponse add(Long salonId, Long userId, TimeOffRequest request) {
		Salon salon = salonService.findOwnedSalon(salonId, userId);
		salonService.lockSalon(salonId);
		checkDates(request);
		Long staffId = request.staffId();
		StaffMember staff = staffId != null ? salonService.findStaff(salonId, staffId) : null;
		List<BookedTime> clashes = activeBookings.inPeriod(salonId, staffId, request.firstDay(), request.lastDay());
		if (!clashes.isEmpty()) {
			BookedTime first = clashes.getFirst();
			throw new ScheduleConflictException(
					clashes.size() + " active booking(s) fall in this time off, the first on " + first.date() + " at "
							+ first.time().start() + ". Cancel them first.");
		}
		TimeOff saved = timeOff.save(new TimeOff(salon, staff, request.firstDay(), request.lastDay()));
		log.info("Time off added: id={} salon={} staff={} from={} to={}", saved.getId(), salonId, staffId,
				saved.getFirstDay(), saved.getLastDay());
		return TimeOffResponse.from(saved);
	}

	/** Time off that hasn't ended yet, soonest first. Owner only. */
	public List<TimeOffResponse> listUpcoming(Long salonId, Long userId) {
		salonService.findOwnedSalon(salonId, userId);
		return timeOff.findBySalonIdAndLastDayGreaterThanEqualOrderByFirstDayAscIdAsc(salonId, LocalDate.now(clock))
			.stream()
			.map(TimeOffResponse::from)
			.toList();
	}

	/** Removing time off can't strand anyone, so it is always allowed. */
	@Transactional
	public void delete(Long salonId, Long timeOffId, Long userId) {
		salonService.findOwnedSalon(salonId, userId);
		TimeOff found = timeOff.findById(timeOffId)
			.filter(entry -> salonId.equals(entry.getSalon().getId()))
			.orElseThrow(() -> new NotFoundException("Time off " + timeOffId + " not found in salon " + salonId));
		timeOff.delete(found);
		log.info("Time off deleted: id={} salon={}", timeOffId, salonId);
	}

	/** Everyone's time off that includes the date, for working out free slots. */
	public List<TimeOff> onDate(Long salonId, LocalDate date) {
		return timeOff.findOnDate(salonId, date);
	}

	private void checkDates(TimeOffRequest request) {
		if (request.lastDay().isBefore(LocalDate.now(clock))) {
			throw new InvalidTimeOffException("Time off ending on " + request.lastDay() + " is already over");
		}
		if (ChronoUnit.DAYS.between(request.firstDay(), request.lastDay()) >= MAX_DAYS) {
			throw new InvalidTimeOffException("Time off can be at most " + MAX_DAYS + " days");
		}
	}

}
