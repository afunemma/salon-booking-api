package io.github.afunemma.salonbooking.salon;

import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.afunemma.salonbooking.booking.OpeningHours;
import io.github.afunemma.salonbooking.common.NotFoundException;
import io.github.afunemma.salonbooking.salon.SalonDtos.CreateSalonRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.CreateServiceRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.SalonResponse;
import io.github.afunemma.salonbooking.salon.SalonDtos.ServiceResponse;

@Service
@Transactional(readOnly = true)
public class SalonService {

	private final SalonRepository salons;
	private final ServiceOfferingRepository services;

	SalonService(SalonRepository salons, ServiceOfferingRepository services) {
		this.salons = salons;
		this.services = services;
	}

	@Transactional
	public SalonResponse createSalon(CreateSalonRequest request) {
		OpeningHours hours = new OpeningHours(request.opensAt(), request.closesAt());
		return SalonResponse.from(salons.save(new Salon(request.name(), hours)));
	}

	public SalonResponse getSalon(Long salonId) {
		return SalonResponse.from(findSalon(salonId));
	}

	@Transactional
	public ServiceResponse addService(Long salonId, CreateServiceRequest request) {
		Salon salon = findSalon(salonId);
		ServiceOffering service = new ServiceOffering(salon, request.name(),
				Duration.ofMinutes(request.durationMinutes()), request.priceFromCents(), request.priceToCents());
		return ServiceResponse.from(services.save(service));
	}

	public List<ServiceResponse> listServices(Long salonId) {
		findSalon(salonId);
		return services.findBySalonIdOrderByName(salonId).stream().map(ServiceResponse::from).toList();
	}

	public Salon findSalon(Long salonId) {
		return salons.findById(salonId).orElseThrow(() -> new NotFoundException("Salon " + salonId + " not found"));
	}

	/**
	 * Locks the salon until the current transaction ends, so bookings for the same
	 * salon are handled one at a time. Must be called inside a transaction.
	 */
	public void lockSalon(Long salonId) {
		salons.findByIdForUpdate(salonId)
				.orElseThrow(() -> new NotFoundException("Salon " + salonId + " not found"));
	}

	/** Finds a service and checks it belongs to the given salon, so one salon can't book another's services. */
	public ServiceOffering findService(Long salonId, Long serviceId) {
		return services.findById(serviceId)
				.filter(service -> service.getSalon().getId().equals(salonId))
				.orElseThrow(() -> new NotFoundException("Service " + serviceId + " not found in salon " + salonId));
	}
}
