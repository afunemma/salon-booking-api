package io.github.afunemma.salonbooking.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.afunemma.salonbooking.common.AppProperties;
import io.github.afunemma.salonbooking.common.AppProperties.RateLimit;
import io.github.afunemma.salonbooking.common.TooManyRequestsException;

class LoginAttemptLimiterTest {

	/** 3 attempts per IP per hour; 2 failures per email per hour. */
	private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(
			new AppProperties(ZoneId.of("Africa/Johannesburg"), new AppProperties.Booking(Duration.ofMinutes(15)),
					new AppProperties.Security(null, Duration.ofHours(1), new RateLimit(3, Duration.ofHours(1)),
							new RateLimit(2, Duration.ofHours(1)))));

	@Test
	@DisplayName("One IP address gets a limited number of attempts, across any emails")
	void limitsAttemptsPerIp() {
		limiter.checkAllowed("10.0.0.1", "a@example.com");
		limiter.checkAllowed("10.0.0.1", "b@example.com");
		limiter.checkAllowed("10.0.0.1", "c@example.com");

		assertThatThrownBy(() -> limiter.checkAllowed("10.0.0.1", "d@example.com")).isInstanceOfSatisfying(
				TooManyRequestsException.class,
				ex -> assertThat(ex.getRetryAfter()).isPositive().isLessThanOrEqualTo(Duration.ofHours(1)));
		// A different IP address is not affected.
		assertThatCode(() -> limiter.checkAllowed("10.0.0.2", "d@example.com")).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("Repeated failures pause that email, whatever its capitalisation")
	void pausesEmailAfterFailures() {
		limiter.recordFailure("thabo@example.com");
		limiter.recordFailure("Thabo@Example.com");

		assertThatThrownBy(() -> limiter.checkAllowed("10.0.0.3", "THABO@example.com"))
			.isInstanceOf(TooManyRequestsException.class);
		assertThatCode(() -> limiter.checkAllowed("10.0.0.4", "someone-else@example.com")).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("A successful login resets the failure count")
	void successResetsFailures() {
		limiter.recordFailure("lerato@example.com");
		limiter.recordSuccess("lerato@example.com");
		limiter.recordFailure("lerato@example.com");

		assertThatCode(() -> limiter.checkAllowed("10.0.0.5", "lerato@example.com")).doesNotThrowAnyException();
	}

}
