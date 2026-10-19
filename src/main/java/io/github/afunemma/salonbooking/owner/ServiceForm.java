package io.github.afunemma.salonbooking.owner;

import java.math.BigDecimal;

import org.jspecify.annotations.Nullable;

import io.github.afunemma.salonbooking.salon.SalonDtos.ServiceRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.ServiceResponse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The service form on the owner pages. Owners type prices in rand (75.50); the API and
 * database use cents (7550), so {@link #toRequest()} converts. BigDecimal keeps 75.50
 * exact, where a double could turn it into 75.4999…
 *
 * @param price the price, or the lowest price of a range; empty for no price
 * @param priceUpTo the highest price of a range; empty for one price
 */
public record ServiceForm(@NotBlank @Size(max = 255) String name,
		@NotNull @Positive @Max(value = 720, message = "must be at most 12 hours") Integer durationMinutes,
		@Nullable @DecimalMin(value = "0", message = "must not be negative") @Digits(integer = 6, fraction = 2,
				message = "must be in rand, with at most 2 decimals, e.g. 75.50") BigDecimal price,
		@Nullable @DecimalMin(value = "0", message = "must not be negative") @Digits(integer = 6, fraction = 2,
				message = "must be in rand, with at most 2 decimals, e.g. 75.50") BigDecimal priceUpTo) {

	static ServiceForm empty() {
		return new ServiceForm("", 30, null, null);
	}

	static ServiceForm from(ServiceResponse service) {
		return new ServiceForm(service.name(), service.durationMinutes(), rand(service.priceFromCents()),
				rand(service.priceToCents()));
	}

	@AssertTrue(message = "\"Up to\" needs a price, and must not be lower than it")
	public boolean isPriceRangeValid() {
		if (priceUpTo == null) {
			return true;
		}
		return price != null && priceUpTo.compareTo(price) >= 0;
	}

	ServiceRequest toRequest() {
		return new ServiceRequest(name.strip(), durationMinutes, cents(price), cents(priceUpTo));
	}

	private static @Nullable Integer cents(@Nullable BigDecimal rand) {
		return rand == null ? null : rand.movePointRight(2).intValueExact();
	}

	private static @Nullable BigDecimal rand(@Nullable Integer cents) {
		return cents == null ? null : BigDecimal.valueOf(cents, 2);
	}

}
