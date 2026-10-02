package io.github.afunemma.salonbooking.salon;

import java.time.Duration;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Something a salon offers, such as "Haircut" (35 min) or "Box braids" (5 hours).
 * <p>
 * Named {@code ServiceOffering} rather than {@code Service} so it is not confused
 * with Spring's {@code @Service} annotation.
 * <p>
 * Prices are stored in cents to avoid floating-point rounding errors. Many salons
 * quote a range ("R50 to R100") rather than a fixed price, so both ends are optional.
 */
@Entity
@Table(name = "service_offering")
public class ServiceOffering {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private @Nullable Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "salon_id")
	private Salon salon;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private int durationMinutes;

	private @Nullable Integer priceFromCents;

	private @Nullable Integer priceToCents;

	protected ServiceOffering() {
	}

	public ServiceOffering(Salon salon, String name, Duration duration, @Nullable Integer priceFromCents,
			@Nullable Integer priceToCents) {
		this.salon = Objects.requireNonNull(salon, "salon must not be null");
		this.name = Objects.requireNonNull(name, "name must not be null");
		if (duration.toMinutes() < 1) {
			throw new IllegalArgumentException("duration must be at least one minute");
		}
		if (priceFromCents != null && priceToCents != null && priceToCents < priceFromCents) {
			throw new IllegalArgumentException("priceToCents must not be lower than priceFromCents");
		}
		this.durationMinutes = Math.toIntExact(duration.toMinutes());
		this.priceFromCents = priceFromCents;
		this.priceToCents = priceToCents;
	}

	/** Only available once saved; the database assigns the id. */
	public Long getId() {
		return Objects.requireNonNull(id, "not saved yet");
	}

	public Salon getSalon() {
		return salon;
	}

	public String getName() {
		return name;
	}

	public Duration getDuration() {
		return Duration.ofMinutes(durationMinutes);
	}

	public @Nullable Integer getPriceFromCents() {
		return priceFromCents;
	}

	public @Nullable Integer getPriceToCents() {
		return priceToCents;
	}
}
