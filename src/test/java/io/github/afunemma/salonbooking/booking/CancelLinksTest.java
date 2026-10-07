package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.afunemma.salonbooking.common.AppProperties;

class CancelLinksTest {

	private static final String SECRET = "test-secret-that-is-at-least-32-characters";

	private final CancelLinks links = links("https://salon.example.com/", SECRET);

	@Test
	@DisplayName("A link points at the booking's cancel page and its token checks out")
	void linkForBooking() {
		assertThat(links.urlFor(42L)).startsWith("https://salon.example.com/bookings/42/cancel?token=")
			.endsWith(links.tokenFor(42L));
		assertThat(links.isValid(42L, links.tokenFor(42L))).isTrue();
	}

	@Test
	@DisplayName("A token is short enough for an SMS: 128 bits in 22 URL-safe characters")
	void tokenIsShortAndUrlSafe() {
		assertThat(links.tokenFor(42L)).hasSize(22).matches("[A-Za-z0-9_-]+");
	}

	@Test
	@DisplayName("One booking's token doesn't work for another booking")
	void tokenOnlyWorksForItsBooking() {
		assertThat(links.isValid(43L, links.tokenFor(42L))).isFalse();
	}

	@Test
	@DisplayName("Tokens made with a different key are rejected, so links can't be forged")
	void otherKeyIsRejected() {
		CancelLinks forger = links("https://salon.example.com", "a-different-secret-of-at-least-32-chars");

		assertThat(links.isValid(42L, forger.tokenFor(42L))).isFalse();
	}

	@Test
	@DisplayName("Garbage, empty and cut-off tokens are rejected without an error")
	void garbageIsRejected() {
		String token = links.tokenFor(42L);

		assertThat(links.isValid(42L, "not base64 !!")).isFalse();
		assertThat(links.isValid(42L, "")).isFalse();
		assertThat(links.isValid(42L, token.substring(0, 10))).isFalse();
	}

	@Test
	@DisplayName("Without a configured secret, a random key still makes working links")
	void randomKeyWhenNoSecret() {
		CancelLinks random = links("http://localhost:8080", null);

		assertThat(random.isValid(42L, random.tokenFor(42L))).isTrue();
		assertThat(links.isValid(42L, random.tokenFor(42L))).isFalse();
	}

	private static CancelLinks links(String baseUrl, @Nullable String secret) {
		return new CancelLinks(new AppProperties(ZoneId.of("Africa/Johannesburg"), URI.create(baseUrl),
				new AppProperties.Booking(Duration.ofMinutes(15)), new AppProperties.Reminders("-"),
				new AppProperties.Security(null, secret, Duration.ofHours(1),
						new AppProperties.RateLimit(20, Duration.ofMinutes(1)),
						new AppProperties.RateLimit(5, Duration.ofMinutes(15)))));
	}

}
