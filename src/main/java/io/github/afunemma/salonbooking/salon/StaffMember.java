package io.github.afunemma.salonbooking.salon;

import java.time.LocalTime;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.afunemma.salonbooking.scheduling.OpeningHours;
import io.github.afunemma.salonbooking.scheduling.TimeRange;
import io.github.afunemma.salonbooking.scheduling.WorkSchedule;
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
 * Someone who serves clients at a salon, with their own weekly schedule: working days,
 * daily hours (e.g. 12:00 to 20:00, always within the salon's opening hours) and an
 * optional break.
 * <p>
 * Every salon gets one staff member when it is created, so a salon of one person never
 * has to set anything up.
 */
@Entity
@Table(name = "staff_member")
public class StaffMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private @Nullable Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "salon_id")
	private Salon salon;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private LocalTime startsAt;

	@Column(nullable = false)
	private LocalTime endsAt;

	/** Bit mask of working days, see {@link WorkSchedule#toMask}. */
	@Column(nullable = false)
	private short workingDays;

	private @Nullable LocalTime breakStartsAt;

	private @Nullable LocalTime breakEndsAt;

	protected StaffMember() {
	}

	/** Works every day of the week, these hours, with no break. */
	public StaffMember(Salon salon, String name, OpeningHours workingHours) {
		this(salon, name, WorkSchedule.everyDay(workingHours));
	}

	public StaffMember(Salon salon, String name, WorkSchedule schedule) {
		this.salon = Objects.requireNonNull(salon, "salon must not be null");
		checkWithinOpeningHours(salon, schedule.hours());
		this.name = Objects.requireNonNull(name, "name must not be null");
		setSchedule(schedule);
	}

	void update(String name, WorkSchedule schedule) {
		checkWithinOpeningHours(salon, schedule.hours());
		this.name = Objects.requireNonNull(name, "name must not be null");
		setSchedule(schedule);
	}

	private void setSchedule(WorkSchedule schedule) {
		this.startsAt = schedule.hours().open();
		this.endsAt = schedule.hours().close();
		this.workingDays = WorkSchedule.toMask(schedule.days());
		TimeRange breakTime = schedule.breakTime();
		this.breakStartsAt = breakTime != null ? breakTime.start() : null;
		this.breakEndsAt = breakTime != null ? breakTime.end() : null;
	}

	/**
	 * @throws InvalidWorkingHoursException if the hours fall outside the salon's opening
	 * hours
	 */
	private static void checkWithinOpeningHours(Salon salon, OpeningHours workingHours) {
		OpeningHours salonHours = salon.getOpeningHours();
		if (workingHours.open().isBefore(salonHours.open()) || workingHours.close().isAfter(salonHours.close())) {
			throw new InvalidWorkingHoursException("Working hours " + workingHours.open() + "–" + workingHours.close()
					+ " must be within the salon's opening hours " + salonHours.open() + "–" + salonHours.close());
		}
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

	public OpeningHours getWorkingHours() {
		return new OpeningHours(startsAt, endsAt);
	}

	public WorkSchedule getSchedule() {
		LocalTime breakStart = breakStartsAt;
		LocalTime breakEnd = breakEndsAt;
		TimeRange breakTime = breakStart != null && breakEnd != null ? new TimeRange(breakStart, breakEnd) : null;
		return new WorkSchedule(WorkSchedule.fromMask(workingDays), getWorkingHours(), breakTime);
	}

}
