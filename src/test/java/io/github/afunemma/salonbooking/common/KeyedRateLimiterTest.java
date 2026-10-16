package io.github.afunemma.salonbooking.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.afunemma.salonbooking.common.AppProperties.RateLimit;

class KeyedRateLimiterTest {

	/** 2 per hour. */
	private final KeyedRateLimiter limiter = new KeyedRateLimiter(new RateLimit(2, Duration.ofHours(1)), "Slow down");

	@Test
	@DisplayName("Allows the limit, then refuses with the message and how long to wait")
	void consumeUpToTheLimit() {
		limiter.consume("10.0.0.1");
		limiter.consume("10.0.0.1");

		assertThatThrownBy(() -> limiter.consume("10.0.0.1")).isInstanceOfSatisfying(TooManyRequestsException.class,
				ex -> {
					assertThat(ex).hasMessage("Slow down");
					// One token refills every 30 minutes.
					assertThat(ex.getRetryAfter()).isBetween(Duration.ofMinutes(29), Duration.ofMinutes(30));
				});
	}

	@Test
	@DisplayName("Each key has its own count")
	void keysAreIndependent() {
		limiter.consume("10.0.0.1");
		limiter.consume("10.0.0.1");

		assertThatCode(() -> limiter.consume("10.0.0.2")).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("check() looks without counting; record() counts without failing; reset() forgets")
	void checkRecordAndReset() {
		limiter.check("thabo");
		limiter.check("thabo");
		limiter.check("thabo");
		limiter.record("thabo");
		limiter.record("thabo");
		limiter.record("thabo");

		assertThatThrownBy(() -> limiter.check("thabo")).isInstanceOf(TooManyRequestsException.class);
		limiter.reset("thabo");
		assertThatCode(() -> limiter.check("thabo")).doesNotThrowAnyException();
	}

}
