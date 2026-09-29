package io.github.afunemma.salonbooking;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Freezes "now" at Sunday 6 January 2030, 10:00 in Johannesburg, so tests about
 * past dates and today's remaining slots give the same result every time.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

	public static final ZonedDateTime NOW = ZonedDateTime.of(2030, 1, 6, 10, 0, 0, 0,
			ZoneId.of("Africa/Johannesburg"));

	@Bean
	@Primary
	Clock fixedClock() {
		return Clock.fixed(NOW.toInstant(), NOW.getZone());
	}
}
