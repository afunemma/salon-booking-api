package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.afunemma.salonbooking.booking.BookingDtos.NoShowStatsResponse;

class NoShowRateTest {

	@Test
	@DisplayName("Rate is no-shows out of all appointments with an outcome, as a percentage")
	void rateIsNoShowsOutOfAppointments() {
		assertThat(NoShowStatsResponse.noShowRatePercent(3, 1)).isEqualTo(25.0);
		assertThat(NoShowStatsResponse.noShowRatePercent(0, 2)).isEqualTo(100.0);
		assertThat(NoShowStatsResponse.noShowRatePercent(5, 0)).isEqualTo(0.0);
	}

	@Test
	@DisplayName("Rate is rounded to one decimal")
	void rateIsRounded() {
		assertThat(NoShowStatsResponse.noShowRatePercent(2, 1)).isEqualTo(33.3);
		assertThat(NoShowStatsResponse.noShowRatePercent(1, 2)).isEqualTo(66.7);
	}

	@Test
	@DisplayName("With no appointments there is no rate, rather than a misleading 0%")
	void noAppointmentsMeansNoRate() {
		assertThat(NoShowStatsResponse.noShowRatePercent(0, 0)).isNull();
	}

}
