package io.github.afunemma.salonbooking.common;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;

import org.hibernate.validator.constraints.time.DurationMin;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * All {@code app.*} settings from application.properties in one typed, validated place.
 * <p>
 * A missing or invalid value (e.g. {@code app.booking.slot-step=0m}) stops the app at
 * startup with a clear message, instead of failing later at runtime.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotNull ZoneId timeZone, @NotNull URI publicBaseUrl, @Valid @NotNull Booking booking,
		@Valid @NotNull Reminders reminders, @Valid @NotNull Security security) {

	public record Booking(@NotNull @DurationMin(minutes = 1) Duration slotStep) {
	}

	/**
	 * @param cron when the reminder job runs, in salon time, e.g. {@code 0 0 18-21 * * *}
	 * for every hour from 18:00 to 21:00. {@code -} switches it off. Spring checks the
	 * cron syntax at startup.
	 * @param whatsapp sending reminders by WhatsApp instead of only logging them
	 */
	public record Reminders(@NotBlank String cron, @Valid @NotNull WhatsApp whatsapp) {
	}

	/**
	 * Meta's WhatsApp Cloud API. Off by default: reminders are then only logged.
	 *
	 * @param enabled send real WhatsApp messages
	 * @param apiUrl Meta's Graph API, including its version
	 * @param phoneNumberId the sending WhatsApp number's id, from Meta's API Setup page
	 * @param accessToken permanent token of a Meta system user; set it with the
	 * {@code APP_REMINDERS_WHATSAPP_ACCESS_TOKEN} environment variable, never in a
	 * committed file
	 * @param templateName the approved message template, see ADR-0014
	 * @param templateLanguage the template's language code, e.g. {@code en}
	 * @param defaultCountryCode added to local numbers such as 082 123 4567; 27 = South
	 * Africa
	 */
	public record WhatsApp(boolean enabled, @NotNull URI apiUrl, @Nullable String phoneNumberId,
			@Nullable String accessToken, @NotBlank String templateName, @NotBlank String templateLanguage,
			@NotBlank @Pattern(regexp = "[0-9]{1,3}") String defaultCountryCode) {

		public static final WhatsApp DISABLED = new WhatsApp(false, URI.create("https://graph.facebook.com/v23.0"),
				null, null, "appointment_reminder", "en", "27");

		@AssertTrue(message = "phone-number-id and access-token are required when WhatsApp is enabled")
		public boolean isConfiguredWhenEnabled() {
			return !enabled || (phoneNumberId != null && !phoneNumberId.isBlank() && accessToken != null
					&& !accessToken.isBlank());
		}
	}

	/**
	 * @param jwtSecret key that signs login tokens; at least 32 characters. Set it with
	 * the {@code APP_SECURITY_JWT_SECRET} environment variable, never in a committed
	 * file. If empty, a random key is generated at startup, so tokens stop working after
	 * a restart.
	 * @param cancelLinkSecret key that signs clients' cancel links; at least 32
	 * characters. Set it with {@code APP_SECURITY_CANCEL_LINK_SECRET}. If empty, a random
	 * key is generated at startup, so links sent before a restart stop working.
	 * @param tokenLifetime how long a login token stays valid
	 * @param loginAttemptsPerIp all login attempts allowed from one IP address
	 * @param failedLoginsPerEmail failed login attempts allowed for one email
	 */
	public record Security(@Nullable @Size(min = 32) String jwtSecret,
			@Nullable @Size(min = 32) String cancelLinkSecret,
			@NotNull @DurationMin(minutes = 1) Duration tokenLifetime, @Valid @NotNull RateLimit loginAttemptsPerIp,
			@Valid @NotNull RateLimit failedLoginsPerEmail) {
	}

	/** At most {@code attempts} within {@code window}, e.g. 5 per 15 minutes. */
	public record RateLimit(@Min(1) int attempts, @NotNull @DurationMin(seconds = 1) Duration window) {
	}

}
