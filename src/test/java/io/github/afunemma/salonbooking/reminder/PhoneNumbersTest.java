package io.github.afunemma.salonbooking.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

	@Test
	@DisplayName("Local South African numbers get the country code instead of the leading 0")
	void localNumbers() {
		assertThat(PhoneNumbers.toInternational("082 123 4567", "27")).isEqualTo("27821234567");
		assertThat(PhoneNumbers.toInternational("0821234567", "27")).isEqualTo("27821234567");
	}

	@Test
	@DisplayName("International numbers keep their own country code")
	void internationalNumbers() {
		assertThat(PhoneNumbers.toInternational("+27 82 123 4567", "27")).isEqualTo("27821234567");
		assertThat(PhoneNumbers.toInternational("+44 20 7946 0958", "27")).isEqualTo("442079460958");
		assertThat(PhoneNumbers.toInternational("0044 20 7946 0958", "27")).isEqualTo("442079460958");
	}

	@Test
	@DisplayName("A number without any digits is rejected")
	void noDigits() {
		assertThatThrownBy(() -> PhoneNumbers.toInternational("+ ", "27")).isInstanceOf(IllegalArgumentException.class);
	}

}
