package io.github.afunemma.salonbooking.salon;

import java.time.LocalTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies for the salon endpoints.
 * <p>
 * The API uses these records instead of exposing JPA entities directly, so the database
 * structure can change without breaking API clients.
 */
public final class SalonDtos {

	private SalonDtos() {
	}

	public record CreateSalonRequest(@NotBlank @Size(max = 255) String name, @NotNull LocalTime opensAt,
			@NotNull LocalTime closesAt) {

		// Fields can still be null here: this check runs alongside @NotNull, not after
		// it.
		@JsonIgnore
		@AssertTrue(message = "opensAt must be before closesAt")
		boolean isOpeningHoursValid() {
			return opensAt == null || closesAt == null || opensAt.isBefore(closesAt);
		}
	}

	public record SalonResponse(Long id, String name, LocalTime opensAt, LocalTime closesAt) {

		static SalonResponse from(Salon salon) {
			return new SalonResponse(salon.getId(), salon.getName(), salon.getOpeningHours().open(),
					salon.getOpeningHours().close());
		}
	}

	public record CreateServiceRequest(@NotBlank @Size(max = 255) String name,
			@NotNull @Positive @Max(value = 720, message = "must be at most 12 hours") Integer durationMinutes,
			@PositiveOrZero @Nullable Integer priceFromCents, @PositiveOrZero @Nullable Integer priceToCents) {

		@JsonIgnore
		@AssertTrue(message = "priceToCents must not be lower than priceFromCents")
		boolean isPriceRangeValid() {
			return priceFromCents == null || priceToCents == null || priceToCents >= priceFromCents;
		}
	}

	public record ServiceResponse(Long id, String name, int durationMinutes, @Nullable Integer priceFromCents,
			@Nullable Integer priceToCents) {

		static ServiceResponse from(ServiceOffering service) {
			return new ServiceResponse(service.getId(), service.getName(),
					Math.toIntExact(service.getDuration().toMinutes()), service.getPriceFromCents(),
					service.getPriceToCents());
		}
	}

}
