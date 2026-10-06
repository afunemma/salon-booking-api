package io.github.afunemma.salonbooking.common;

import java.time.Duration;
import java.time.ZoneId;

import org.hibernate.validator.constraints.time.DurationMin;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * All {@code app.*} settings from application.properties in one typed, validated place.
 * <p>
 * A missing or invalid value (e.g. {@code app.booking.slot-step=0m}) stops the app at
 * startup with a clear message, instead of failing later at runtime.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotNull ZoneId timeZone, @Valid @NotNull Booking booking,
		@Valid @NotNull Reminders reminders, @Valid @NotNull Security security) {

	public record Booking(@NotNull @DurationMin(minutes = 1) Duration slotStep) {
	}

	/**
	 * @param cron when the reminder job runs, in salon time, e.g. {@code 0 0 18-21 * * *}
	 * for every hour from 18:00 to 21:00. {@code -} switches it off. Spring checks the
	 * cron syntax at startup.
	 */
	public record Reminders(@NotBlank String cron) {
	}

	/**
	 * @param jwtSecret key that signs login tokens; at least 32 characters. Set it with
	 * the {@code APP_SECURITY_JWT_SECRET} environment variable, never in a committed
	 * file. If empty, a random key is generated at startup, so tokens stop working after
	 * a restart.
	 * @param tokenLifetime how long a login token stays valid
	 * @param loginAttemptsPerIp all login attempts allowed from one IP address
	 * @param failedLoginsPerEmail failed login attempts allowed for one email
	 */
	public record Security(@Nullable @Size(min = 32) String jwtSecret,
			@NotNull @DurationMin(minutes = 1) Duration tokenLifetime, @Valid @NotNull RateLimit loginAttemptsPerIp,
			@Valid @NotNull RateLimit failedLoginsPerEmail) {
	}

	/** At most {@code attempts} within {@code window}, e.g. 5 per 15 minutes. */
	public record RateLimit(@Min(1) int attempts, @NotNull @DurationMin(seconds = 1) Duration window) {
	}

}
