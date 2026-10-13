package io.github.afunemma.salonbooking.scheduling;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * When someone works in a normal week: on which days, between which times, and with an
 * optional daily break, e.g. Tuesday to Saturday, 09:00 to 17:00, lunch 13:00 to 13:30.
 */
public record WorkSchedule(Set<DayOfWeek> days, OpeningHours hours, @Nullable TimeRange breakTime) {

	public WorkSchedule {
		Objects.requireNonNull(days, "days must not be null");
		Objects.requireNonNull(hours, "hours must not be null");
		if (days.isEmpty()) {
			throw new IllegalArgumentException("There must be at least one working day");
		}
		days = Collections.unmodifiableSet(EnumSet.copyOf(days));
		if (breakTime != null && (breakTime.start().isBefore(hours.open()) || breakTime.end().isAfter(hours.close()))) {
			throw new IllegalArgumentException("The break " + breakTime.start() + "–" + breakTime.end()
					+ " must be within working hours " + hours.open() + "–" + hours.close());
		}
	}

	/** Every day of the week, these hours, no break. */
	public static WorkSchedule everyDay(OpeningHours hours) {
		return new WorkSchedule(EnumSet.allOf(DayOfWeek.class), hours, null);
	}

	public boolean worksOn(DayOfWeek day) {
		return days.contains(day);
	}

	/** Times on a working day that can't be booked: the break, if there is one. */
	public List<TimeRange> breaks() {
		return breakTime == null ? List.of() : List.of(breakTime);
	}

	/**
	 * Whether an appointment fits this schedule: on a working day, within working hours,
	 * and not during the break. Used to check that a schedule change doesn't strand an
	 * existing booking.
	 */
	public boolean covers(LocalDate date, TimeRange appointment) {
		return worksOn(date.getDayOfWeek()) && !appointment.start().isBefore(hours.open())
				&& !appointment.end().isAfter(hours.close()) && breaks().stream().noneMatch(appointment::overlaps);
	}

	/**
	 * The days as a bit mask, as the database stores them: Monday = 1, Tuesday = 2,
	 * Wednesday = 4, … Sunday = 64. Every day = 127.
	 */
	public static short toMask(Set<DayOfWeek> days) {
		int mask = 0;
		for (DayOfWeek day : days) {
			mask |= 1 << (day.getValue() - 1);
		}
		return (short) mask;
	}

	public static Set<DayOfWeek> fromMask(short mask) {
		Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
		for (DayOfWeek day : DayOfWeek.values()) {
			if ((mask & (1 << (day.getValue() - 1))) != 0) {
				days.add(day);
			}
		}
		return days;
	}

}
