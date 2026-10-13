package io.github.afunemma.salonbooking.salon;

import java.time.LocalDate;
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
 * Days when a staff member is away (leave), or, without a staff member, when the whole
 * salon is closed (e.g. a public holiday). Both the first and the last day are included.
 */
@Entity
@Table(name = "time_off")
public class TimeOff {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private @Nullable Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "salon_id")
	private Salon salon;

	/** {@code null} = the whole salon is closed. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "staff_member_id")
	private @Nullable StaffMember staff;

	@Column(nullable = false)
	private LocalDate firstDay;

	@Column(nullable = false)
	private LocalDate lastDay;

	protected TimeOff() {
	}

	TimeOff(Salon salon, @Nullable StaffMember staff, LocalDate firstDay, LocalDate lastDay) {
		this.salon = Objects.requireNonNull(salon, "salon must not be null");
		this.staff = staff;
		this.firstDay = Objects.requireNonNull(firstDay, "firstDay must not be null");
		this.lastDay = Objects.requireNonNull(lastDay, "lastDay must not be null");
		if (lastDay.isBefore(firstDay)) {
			throw new IllegalArgumentException("lastDay must not be before firstDay");
		}
	}

	/** Whether this time off keeps the given staff member away. */
	public boolean appliesTo(StaffMember member) {
		StaffMember away = staff;
		return away == null || away.getId().equals(member.getId());
	}

	/** Only available once saved; the database assigns the id. */
	public Long getId() {
		return Objects.requireNonNull(id, "not saved yet");
	}

	public Salon getSalon() {
		return salon;
	}

	public @Nullable StaffMember getStaff() {
		return staff;
	}

	public LocalDate getFirstDay() {
		return firstDay;
	}

	public LocalDate getLastDay() {
		return lastDay;
	}

}
