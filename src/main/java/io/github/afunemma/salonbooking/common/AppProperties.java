package io.github.afunemma.salonbooking.common;

import java.time.Duration;
import java.time.ZoneId;

import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * All {@code app.*} settings from application.properties in one typed, validated place.
 * <p>
 * A missing or invalid value (e.g. {@code app.booking.slot-step=0m}) stops the app
 * at startup with a clear message, instead of failing later at runtime.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotNull ZoneId timeZone, @Valid @NotNull Booking booking) {

	public record Booking(@NotNull @DurationMin(minutes = 1) Duration slotStep) {
	}
}
