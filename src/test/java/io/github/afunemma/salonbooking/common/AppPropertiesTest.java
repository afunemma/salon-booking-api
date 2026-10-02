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

	private static final String[] VALID = { "app.time-zone=Africa/Johannesburg", "app.booking.slot-step=15m",
			"app.security.token-lifetime=1h" };

	@Test
	void bindsValidSettings() {
		runner.withPropertyValues(VALID).run(context -> {
			AppProperties properties = context.getBean(AppProperties.class);
			assertThat(properties.timeZone()).isEqualTo(ZoneId.of("Africa/Johannesburg"));
			assertThat(properties.booking().slotStep()).isEqualTo(Duration.ofMinutes(15));
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
