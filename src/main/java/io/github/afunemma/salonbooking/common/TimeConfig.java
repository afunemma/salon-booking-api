package io.github.afunemma.salonbooking.common;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the clock used for "now" and "today".
 * <p>
 * Injecting a {@link Clock} instead of calling {@code LocalDate.now()} directly
 * lets tests fix the current time, and keeps the salon's time zone correct even
 * when the server runs in UTC.
 */
@Configuration(proxyBeanMethods = false)
class TimeConfig {

	@Bean
	Clock clock(@Value("${app.time-zone}") String timeZone) {
		return Clock.system(ZoneId.of(timeZone));
	}
}
