package io.github.afunemma.salonbooking.salon;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.afunemma.salonbooking.common.NotFoundException;
import io.github.afunemma.salonbooking.salon.ActiveBookings.BookedTime;
import io.github.afunemma.salonbooking.salon.SalonDtos.CreateSalonRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.ServiceRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.SalonResponse;
import io.github.afunemma.salonbooking.salon.SalonDtos.ServiceResponse;
import io.github.afunemma.salonbooking.salon.SalonDtos.StaffRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.StaffResponse;
import io.github.afunemma.salonbooking.scheduling.OpeningHours;
import io.github.afunemma.salonbooking.scheduling.TimeRange;
import io.github.afunemma.salonbooking.scheduling.WorkSchedule;

@Service
@Transactional(readOnly = true)
public class SalonService {

	private static final Logger log = LoggerFactory.getLogger(SalonService.class);

	private final SalonRepository salons;

	/**
	 * Name of the staff member every new salon starts with. In a salon of one person,
	 * that's the owner; they can rename it.
	 */
	static final String FIRST_STAFF_NAME = "Owner";

	private final ServiceOfferingRepository services;

	private final StaffMemberRepository staff;

	private final ActiveBookings activeBookings;

	private final Clock clock;

	SalonService(SalonRepository salons, ServiceOfferingRepository services, StaffMemberRepository staff,
			ActiveBookings activeBookings, Clock clock) {
		this.salons = salons;
		this.services = services;
		this.staff = staff;
		this.activeBookings = activeBookings;
		this.clock = clock;
	}

	@Transactional
	public SalonResponse createSalon(CreateSalonRequest request, Long ownerId) {
		OpeningHours hours = new OpeningHours(request.opensAt(), request.closesAt());
		Salon salon = salons.save(new Salon(request.name(), hours, ownerId));
		staff.save(new StaffMember(salon, FIRST_STAFF_NAME, hours));
		log.info("Salon created: id={} owner={}", salon.getId(), ownerId);
		return SalonResponse.from(salon);
	}

	public List<SalonResponse> listOwnedSalons(Long ownerId) {
		return salons.findByOwnerIdOrderByName(ownerId).stream().map(SalonResponse::from).toList();
	}

	public SalonResponse getSalon(Long salonId) {
		return SalonResponse.from(findSalon(salonId));
	}

	@Transactional
	public ServiceResponse addService(Long salonId, Long userId, ServiceRequest request) {
		Salon salon = findOwnedSalon(salonId, userId);
		ServiceOffering service = new ServiceOffering(salon, request.name(),
				Duration.ofMinutes(request.durationMinutes()), request.priceFromCents(), request.priceToCents());
		services.save(service);
		log.info("Service added: id={} salon={} duration={}min", service.getId(), salonId,
				service.getDuration().toMinutes());
		return ServiceResponse.from(service);
	}

	/**
	 * Changes a service's name, length or price (owner only). Existing bookings keep the
	 * times they were booked with, so changing the length can't create overlaps.
	 */
	@Transactional
	public ServiceResponse updateService(Long salonId, Long serviceId, Long userId, ServiceRequest request) {
		findOwnedSalon(salonId, userId);
		ServiceOffering service = findService(salonId, serviceId);
		service.update(request.name(), Duration.ofMinutes(request.durationMinutes()), request.priceFromCents(),
				request.priceToCents());
		log.info("Service updated: id={} salon={} duration={}min", serviceId, salonId,
				service.getDuration().toMinutes());
		return ServiceResponse.from(service);
	}

	public List<ServiceResponse> listServices(Long salonId) {
		findSalon(salonId);
		return services.findBySalonIdOrderByName(salonId).stream().map(ServiceResponse::from).toList();
	}

	@Transactional
	public StaffResponse addStaff(Long salonId, Long userId, StaffRequest request) {
		Salon salon = findOwnedSalon(salonId, userId);
		StaffMember member = staff.save(new StaffMember(salon, request.name(), schedule(salon, request)));
		log.info("Staff member added: id={} salon={}", member.getId(), salonId);
		return StaffResponse.from(member);
	}

	@Transactional
	public StaffResponse updateStaff(Long salonId, Long staffId, Long userId, StaffRequest request) {
		Salon salon = findOwnedSalon(salonId, userId);
		// Locked like a booking, so a client can't book a time the new schedule removes
		// between the check below and the save.
		lockSalon(salonId);
		StaffMember member = findStaff(salonId, staffId);
		WorkSchedule schedule = schedule(salon, request);
		checkNoBookingsStranded(staffId, schedule);
		member.update(request.name(), schedule);
		log.info("Staff member updated: id={} salon={}", staffId, salonId);
		return StaffResponse.from(member);
	}

	public List<StaffResponse> listStaff(Long salonId) {
		findSalon(salonId);
		return staffOf(salonId).stream().map(StaffResponse::from).toList();
	}

	/** The salon's staff members, in the order they were added. */
	public List<StaffMember> staffOf(Long salonId) {
		return staff.findBySalonIdOrderById(salonId);
	}

	/**
	 * Finds a staff member and checks they work at the given salon, so one salon can't
	 * book or change another's staff.
	 */
	public StaffMember findStaff(Long salonId, Long staffId) {
		return staff.findById(staffId)
			.filter(member -> salonId.equals(member.getSalon().getId()))
			.orElseThrow(() -> new NotFoundException("Staff member " + staffId + " not found in salon " + salonId));
	}

	/**
	 * Refuses a schedule change that would leave active bookings on a day off, outside
	 * working hours or during the break: a client with a confirmed booking must never
	 * find the salon no longer expects them.
	 */
	private void checkNoBookingsStranded(Long staffId, WorkSchedule schedule) {
		List<BookedTime> stranded = activeBookings.forStaffFrom(staffId, LocalDate.now(clock))
			.stream()
			.filter(booking -> !schedule.covers(booking.date(), booking.time()))
			.toList();
		if (!stranded.isEmpty()) {
			BookedTime first = stranded.getFirst();
			throw new ScheduleConflictException(stranded.size() + " active booking(s) would fall outside the new "
					+ "schedule, the first on " + first.date() + " at " + first.time().start()
					+ ". Cancel them first, or change the schedule so they still fit.");
		}
	}

	/**
	 * Builds the schedule from a request. Left-out fields default to the salon's opening
	 * and closing times, every day of the week, and no break.
	 */
	private static WorkSchedule schedule(Salon salon, StaffRequest request) {
		OpeningHours salonHours = salon.getOpeningHours();
		OpeningHours hours = new OpeningHours(request.startsAt() != null ? request.startsAt() : salonHours.open(),
				request.endsAt() != null ? request.endsAt() : salonHours.close());
		Set<DayOfWeek> days = request.workingDays() != null ? request.workingDays() : EnumSet.allOf(DayOfWeek.class);
		LocalTime breakStart = request.breakStartsAt();
		LocalTime breakEnd = request.breakEndsAt();
		TimeRange breakTime = breakStart != null && breakEnd != null ? new TimeRange(breakStart, breakEnd) : null;
		try {
			return new WorkSchedule(days, hours, breakTime);
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidWorkingHoursException(ex.getMessage());
		}
	}

	public Salon findSalon(Long salonId) {
		return salons.findById(salonId).orElseThrow(() -> new NotFoundException("Salon " + salonId + " not found"));
	}

	/**
	 * Finds a salon and checks the user owns it. Every owner-only operation goes through
	 * here, so one owner can never see or change another owner's salon.
	 * @throws AccessDeniedException (HTTP 403) if the user doesn't own the salon
	 */
	public Salon findOwnedSalon(Long salonId, Long userId) {
		Salon salon = findSalon(salonId);
		if (!salon.isOwnedBy(userId)) {
			log.warn("Access denied: user={} tried to manage salon={}", userId, salonId);
			throw new AccessDeniedException("User " + userId + " does not own salon " + salonId);
		}
		return salon;
	}

	/**
	 * Locks the salon until the current transaction ends, so bookings for the same salon
	 * are handled one at a time. Must be called inside a transaction.
	 */
	public void lockSalon(Long salonId) {
		salons.findByIdForUpdate(salonId).orElseThrow(() -> new NotFoundException("Salon " + salonId + " not found"));
	}

	/**
	 * Finds a service and checks it belongs to the given salon, so one salon can't book
	 * another's services.
	 */
	public ServiceOffering findService(Long salonId, Long serviceId) {
		return services.findById(serviceId)
			.filter(service -> salonId.equals(service.getSalon().getId()))
			.orElseThrow(() -> new NotFoundException("Service " + serviceId + " not found in salon " + salonId));
	}

}
