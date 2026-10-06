package io.github.afunemma.salonbooking.reminder;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The reminder for one booking: whether it was sent, and how many attempts it took.
 * <p>
 * Stored as a booking id rather than a reference to the booking entity, so the booking
 * package doesn't need to know reminders exist.
 */
@Entity
@Table(name = "booking_reminder")
public class BookingReminder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private @Nullable Long id;

	@Column(nullable = false, updatable = false)
	private Long bookingId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ReminderStatus status;

	@Column(nullable = false)
	private int attempts;

	@Column(nullable = false)
	private Instant lastAttemptAt;

	private @Nullable Instant sentAt;

	protected BookingReminder() {
	}

	/** Records the first attempt to remind the client of a booking. */
	BookingReminder(Long bookingId, Instant attemptedAt, boolean delivered) {
		this.bookingId = Objects.requireNonNull(bookingId, "bookingId must not be null");
		this.attempts = 1;
		this.lastAttemptAt = attemptedAt;
		this.status = delivered ? ReminderStatus.SENT : ReminderStatus.FAILED;
		this.sentAt = delivered ? attemptedAt : null;
	}

	/** Records a retry after an earlier attempt failed. */
	void recordRetry(Instant attemptedAt, boolean delivered) {
		if (status == ReminderStatus.SENT) {
			throw new IllegalStateException("Reminder for booking " + bookingId + " was already sent");
		}
		attempts++;
		lastAttemptAt = attemptedAt;
		if (delivered) {
			status = ReminderStatus.SENT;
			sentAt = attemptedAt;
		}
	}

	public Long getBookingId() {
		return bookingId;
	}

	public ReminderStatus getStatus() {
		return status;
	}

	public int getAttempts() {
		return attempts;
	}

	public Instant getLastAttemptAt() {
		return lastAttemptAt;
	}

	public @Nullable Instant getSentAt() {
		return sentAt;
	}

}
