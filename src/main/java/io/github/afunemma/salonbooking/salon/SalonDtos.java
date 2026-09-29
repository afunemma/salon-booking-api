package io.github.afunemma.salonbooking.salon;

import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies for the salon endpoints.
 * <p>
 * The API uses these records instead of exposing JPA entities directly, so the
 * database structure can change without breaking API clients.
 */
public final class SalonDtos {

	private SalonDtos() {
	}

	public record CreateSalonRequest(
			@NotBlank @Size(max = 255) String name,
			@NotNull LocalTime opensAt,
			@NotNull LocalTime closesAt) {
	}

	public record SalonResponse(Long id, String name, LocalTime opensAt, LocalTime closesAt) {

		static SalonResponse from(Salon salon) {
			return new SalonResponse(salon.getId(), salon.getName(), salon.getOpeningHours().open(),
					salon.getOpeningHours().close());
		}
	}

	public record CreateServiceRequest(
			@NotBlank @Size(max = 255) String name,
			@NotNull @Positive @Max(value = 720, message = "must be at most 12 hours") Integer durationMinutes,
			@PositiveOrZero Integer priceFromCents,
			@PositiveOrZero Integer priceToCents) {
	}

	public record ServiceResponse(Long id, String name, int durationMinutes, Integer priceFromCents,
			Integer priceToCents) {

		static ServiceResponse from(ServiceOffering service) {
			return new ServiceResponse(service.getId(), service.getName(),
					Math.toIntExact(service.getDuration().toMinutes()), service.getPriceFromCents(),
					service.getPriceToCents());
		}
	}
}
