package io.github.afunemma.salonbooking.booking;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.afunemma.salonbooking.salon.Salon;
import io.github.afunemma.salonbooking.salon.ServiceOffering;
import io.github.afunemma.salonbooking.scheduling.TimeRange;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A client's appointment at a salon.
 * <p>
 * The end time is stored (not just calculated) so the database can check for overlapping
 * bookings later on.
 */
@Entity
@Table(name = "booking")
public class Booking {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private @Nullable Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "salon_id")
	private Salon salon;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "service_offering_id")
	private ServiceOffering service;

	@Column(nullable = false)
	private String clientName;

	@Column(nullable = false)
	private String clientPhone;

	@Column(nullable = false)
	private LocalDate bookingDate;

	@Column(nullable = false)
	private LocalTime startTime;

	@Column(nullable = false)
	private LocalTime endTime;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private BookingStatus status;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected Booking() {
	}

	public Booking(ServiceOffering service, String clientName, String clientPhone, LocalDate bookingDate,
			LocalTime startTime) {
		this.service = Objects.requireNonNull(service, "service must not be null");
		this.salon = service.getSalon();
		this.clientName = Objects.requireNonNull(clientName, "clientName must not be null");
		this.clientPhone = Objects.requireNonNull(clientPhone, "clientPhone must not be null");
		this.bookingDate = Objects.requireNonNull(bookingDate, "bookingDate must not be null");
		this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
		this.endTime = startTime.plus(service.getDuration());
		this.status = BookingStatus.BOOKED;
		this.createdAt = Instant.now();
	}

	public TimeRange getTimeRange() {
		return new TimeRange(startTime, endTime);
	}

	public void cancel() {
		changeStatus(BookingStatus.CANCELLED);
	}

	public void markCompleted() {
		changeStatus(BookingStatus.COMPLETED);
	}

	public void markNoShow() {
		changeStatus(BookingStatus.NO_SHOW);
	}

	private void changeStatus(BookingStatus newStatus) {
		if (status != BookingStatus.BOOKED) {
			throw new InvalidBookingStateException("Booking " + id + " is already " + status);
		}
		status = newStatus;
	}

	/** Only available once saved; the database assigns the id. */
	public Long getId() {
		return Objects.requireNonNull(id, "not saved yet");
	}

	public Salon getSalon() {
		return salon;
	}

	public ServiceOffering getService() {
		return service;
	}

	public String getClientName() {
		return clientName;
	}

	public String getClientPhone() {
		return clientPhone;
	}

	public LocalDate getBookingDate() {
		return bookingDate;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public BookingStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
