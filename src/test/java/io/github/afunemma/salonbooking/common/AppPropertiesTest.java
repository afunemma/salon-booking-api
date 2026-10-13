package io.github.afunemma.salonbooking.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Checks that bad configuration stops the app at startup instead of failing later.
 */
class AppPropertiesTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

	private static final String[] VALID = { "app.time-zone=Africa/Johannesburg",
			"app.public-base-url=https://salon.example.com", "app.booking.slot-step=15m",
			"app.reminders.cron=0 0 18-21 * * *", "app.reminders.whatsapp.api-url=https://graph.facebook.com/v23.0",
			"app.reminders.whatsapp.template-name=appointment_reminder", "app.reminders.whatsapp.template-language=en",
			"app.reminders.whatsapp.default-country-code=27", "app.security.token-lifetime=1h",
			"app.security.login-attempts-per-ip.attempts=20", "app.security.login-attempts-per-ip.window=1m",
			"app.security.failed-logins-per-email.attempts=5", "app.security.failed-logins-per-email.window=15m" };

	@Test
	void bindsValidSettings() {
		runner.withPropertyValues(VALID).run(context -> {
			AppProperties properties = context.getBean(AppProperties.class);
			assertThat(properties.timeZone()).isEqualTo(ZoneId.of("Africa/Johannesburg"));
			assertThat(properties.booking().slotStep()).isEqualTo(Duration.ofMinutes(15));
			assertThat(properties.reminders().cron()).isEqualTo("0 0 18-21 * * *");
			assertThat(properties.security().jwtSecret()).isNull();
			assertThat(properties.security().tokenLifetime()).isEqualTo(Duration.ofHours(1));
		});
	}

	@Test
	void rejectsSlotStepShorterThanAMinute() {
		runner.withPropertyValues(VALID)
			.withPropertyValues("app.booking.slot-step=0m")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsBlankReminderSchedule() {
		runner.withPropertyValues(VALID)
			.withPropertyValues("app.reminders.cron=")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsCancelLinkSecretShorterThan32Characters() {
		runner.withPropertyValues(VALID)
			.withPropertyValues("app.security.cancel-link-secret=too-short")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void whatsAppIsOffByDefault() {
		runner.withPropertyValues(VALID)
			.run(context -> assertThat(context.getBean(AppProperties.class).reminders().whatsapp().enabled())
				.isFalse());
	}

	@Test
	void rejectsWhatsAppSwitchedOnWithoutNumberAndToken() {
		runner.withPropertyValues(VALID)
			.withPropertyValues("app.reminders.whatsapp.enabled=true")
			.run(context -> assertThat(context).hasFailed());
		runner.withPropertyValues(VALID)
			.withPropertyValues("app.reminders.whatsapp.enabled=true", "app.reminders.whatsapp.phone-number-id=123")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void bindsWhatsAppSettingsWhenSwitchedOn() {
		runner.withPropertyValues(VALID)
			.withPropertyValues("app.reminders.whatsapp.enabled=true", "app.reminders.whatsapp.phone-number-id=123",
					"app.reminders.whatsapp.access-token=test-token")
			.run(context -> assertThat(context.getBean(AppProperties.class).reminders().whatsapp().phoneNumberId())
				.isEqualTo("123"));
	}

	@Test
	void rejectsCountryCodeThatIsNotDigits() {
		runner.withPropertyValues(VALID)
			.withPropertyValues("app.reminders.whatsapp.default-country-code=+27")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsRateLimitOfZeroAttempts() {
		runner.withPropertyValues(VALID)
			.withPropertyValues("app.security.failed-logins-per-email.attempts=0")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsJwtSecretShorterThan32Characters() {
		runner.withPropertyValues(VALID)
			.withPropertyValues("app.security.jwt-secret=too-short")
			.run(context -> assertThat(context).hasFailed());
	}

	@Test
	void rejectsMissingSettings() {
		runner.run(context -> assertThat(context).hasFailed());
	}

	@EnableConfigurationProperties(AppProperties.class)
	static class Config {

	}

}
