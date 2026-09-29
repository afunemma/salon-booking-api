package io.github.afunemma.salonbooking.booking;

import java.time.Duration;
import java.util.Objects;

/**
 * Something a salon offers, such as "Haircut" (35 min) or "Box braids" (5 hours).
 * <p>
 * Named {@code ServiceOffering} rather than {@code Service} so it is not confused
 * with Spring's {@code @Service} annotation.
 */
public record ServiceOffering(String name, Duration duration) {

	public ServiceOffering {
		Objects.requireNonNull(name, "name must not be null");
		Objects.requireNonNull(duration, "duration must not be null");
		if (name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		if (duration.isNegative() || duration.isZero()) {
			throw new IllegalArgumentException("duration must be positive");
		}
	}
}
