package io.github.afunemma.salonbooking;

import org.springframework.boot.SpringApplication;

public class TestSalonBookingApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(SalonBookingApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
