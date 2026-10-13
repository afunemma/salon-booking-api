package io.github.afunemma.salonbooking.salon;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.afunemma.salonbooking.common.NotFoundException;
import io.github.afunemma.salonbooking.salon.SalonDtos.CreateSalonRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.CreateServiceRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.SalonResponse;
import io.github.afunemma.salonbooking.salon.SalonDtos.ServiceResponse;
import io.github.afunemma.salonbooking.salon.SalonDtos.StaffRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.StaffResponse;
import io.github.afunemma.salonbooking.scheduling.OpeningHours;

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

	SalonService(SalonRepository salons, ServiceOfferingRepository services, StaffMemberRepository staff) {
		this.salons = salons;
		this.services = services;
		this.staff = staff;
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
	public ServiceResponse addService(Long salonId, Long userId, CreateServiceRequest request) {
		Salon salon = findOwnedSalon(salonId, userId);
		ServiceOffering service = new ServiceOffering(salon, request.name(),
				Duration.ofMinutes(request.durationMinutes()), request.priceFromCents(), request.priceToCents());
		services.save(service);
		log.info("Service added: id={} salon={} duration={}min", service.getId(), salonId,
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
		StaffMember member = staff.save(new StaffMember(salon, request.name(), workingHours(salon, request)));
		log.info("Staff member added: id={} salon={}", member.getId(), salonId);
		return StaffResponse.from(member);
	}

	@Transactional
	public StaffResponse updateStaff(Long salonId, Long staffId, Long userId, StaffRequest request) {
		Salon salon = findOwnedSalon(salonId, userId);
		StaffMember member = findStaff(salonId, staffId);
		member.update(request.name(), workingHours(salon, request));
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

	/** Missing times default to the salon's own opening and closing times. */
	private static OpeningHours workingHours(Salon salon, StaffRequest request) {
		OpeningHours salonHours = salon.getOpeningHours();
		return new OpeningHours(request.startsAt() != null ? request.startsAt() : salonHours.open(),
				request.endsAt() != null ? request.endsAt() : salonHours.close());
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
