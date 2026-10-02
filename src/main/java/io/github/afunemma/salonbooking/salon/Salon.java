package io.github.afunemma.salonbooking.salon;

import java.time.LocalTime;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.afunemma.salonbooking.booking.OpeningHours;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "salon")
public class Salon {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private @Nullable Long id;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private LocalTime opensAt;

	@Column(nullable = false)
	private LocalTime closesAt;

	/** Required by JPA; use the public constructor in application code. */
	protected Salon() {
	}

	public Salon(String name, OpeningHours hours) {
		this.name = name;
		this.opensAt = hours.open();
		this.closesAt = hours.close();
	}

	/** Only available once saved; the database assigns the id. */
	public Long getId() {
		return Objects.requireNonNull(id, "not saved yet");
	}

	public String getName() {
		return name;
	}

	public OpeningHours getOpeningHours() {
		return new OpeningHours(opensAt, closesAt);
	}
}
