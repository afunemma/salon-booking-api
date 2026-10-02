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
import io.github.afunemma.salonbooking.scheduling.OpeningHours;

@Service
@Transactional(readOnly = true)
public class SalonService {

	private static final Logger log = LoggerFactory.getLogger(SalonService.class);

	private final SalonRepository salons;

	private final ServiceOfferingRepository services;

	SalonService(SalonRepository salons, ServiceOfferingRepository services) {
		this.salons = salons;
		this.services = services;
	}

	@Transactional
	public SalonResponse createSalon(CreateSalonRequest request, Long ownerId) {
		OpeningHours hours = new OpeningHours(request.opensAt(), request.closesAt());
		Salon salon = salons.save(new Salon(request.name(), hours, ownerId));
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
