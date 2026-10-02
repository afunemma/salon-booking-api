package io.github.afunemma.salonbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SalonBookingApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(SalonBookingApiApplication.class, args);
	}

}
