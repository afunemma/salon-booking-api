package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.afunemma.salonbooking.booking.ClientBookingController.ServiceOption;
import io.github.afunemma.salonbooking.salon.SalonDtos.ServiceResponse;

class ServiceOptionTest {

	@Test
	@DisplayName("Prices show in rand, with cents only when there are some")
	void formatsRand() {
		assertThat(ServiceOption.rand(5000)).isEqualTo("R50");
		assertThat(ServiceOption.rand(7550)).isEqualTo("R75.50");
		assertThat(ServiceOption.rand(7505)).isEqualTo("R75.05");
		assertThat(ServiceOption.rand(0)).isEqualTo("R0");
	}

	@Test
	@DisplayName("A service reads as name, length and price or price range")
	void labels() {
		assertThat(ServiceOption.from(new ServiceResponse(1L, "Haircut", 35, 5000, 10000)).label())
			.isEqualTo("Haircut · 35 min · R50–R100");
		assertThat(ServiceOption.from(new ServiceResponse(1L, "Manicure", 60, 20000, 20000)).label())
			.isEqualTo("Manicure · 60 min · R200");
		assertThat(ServiceOption.from(new ServiceResponse(1L, "Consultation", 15, null, null)).label())
			.isEqualTo("Consultation · 15 min");
	}

}
