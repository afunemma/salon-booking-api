package io.github.afunemma.salonbooking.scheduling;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WorkScheduleTest {

	/** Monday 7 January 2030. */
	private static final LocalDate MONDAY_DATE = LocalDate.of(2030, 1, 7);

	private static final OpeningHours NINE_TO_FIVE = new OpeningHours(LocalTime.of(9, 0), LocalTime.of(17, 0));

	private static final TimeRange LUNCH = range("13:00", "13:30");

	/** Tuesday to Saturday, 09:00–17:00, lunch 13:00–13:30. */
	private final WorkSchedule schedule = new WorkSchedule(EnumSet.range(TUESDAY, SATURDAY), NINE_TO_FIVE, LUNCH);

	@Test
	@DisplayName("An appointment fits on a working day, inside the hours and outside the break")
	void coversAppointmentsThatFit() {
		LocalDate tuesday = MONDAY_DATE.plusDays(1);

		assertThat(schedule.covers(tuesday, range("10:00", "10:30"))).isTrue();
		assertThat(schedule.covers(tuesday, range("12:30", "13:00"))).as("ends as the break starts").isTrue();
		assertThat(schedule.covers(tuesday, range("13:30", "14:00"))).as("starts as the break ends").isTrue();
		assertThat(schedule.covers(tuesday, range("16:30", "17:00"))).as("ends at closing").isTrue();
	}

	@Test
	@DisplayName("An appointment doesn't fit on a day off, during the break or outside the hours")
	void rejectsAppointmentsThatDontFit() {
		LocalDate tuesday = MONDAY_DATE.plusDays(1);

		assertThat(schedule.covers(MONDAY_DATE, range("10:00", "10:30"))).as("day off").isFalse();
		assertThat(schedule.covers(tuesday, range("12:45", "13:15"))).as("overlaps the break").isFalse();
		assertThat(schedule.covers(tuesday, range("08:30", "09:30"))).as("starts too early").isFalse();
		assertThat(schedule.covers(tuesday, range("16:45", "17:15"))).as("ends too late").isFalse();
	}

	@Test
	@DisplayName("Working days are stored as a bit mask: Monday = 1 … Sunday = 64, every day = 127")
	void daysRoundTripThroughBitMask() {
		assertThat(WorkSchedule.toMask(Set.of(MONDAY))).isEqualTo((short) 1);
		assertThat(WorkSchedule.toMask(Set.of(SUNDAY))).isEqualTo((short) 64);
		assertThat(WorkSchedule.toMask(EnumSet.allOf(DayOfWeek.class))).isEqualTo((short) 127);
		assertThat(WorkSchedule.fromMask(WorkSchedule.toMask(Set.of(MONDAY, FRIDAY)))).containsExactlyInAnyOrder(MONDAY,
				FRIDAY);
	}

	@Test
	@DisplayName("A schedule needs at least one working day, and a break inside working hours")
	void rejectsInvalidSchedules() {
		assertThatThrownBy(() -> new WorkSchedule(Set.of(), NINE_TO_FIVE, null))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new WorkSchedule(Set.of(MONDAY), NINE_TO_FIVE, range("16:45", "17:15")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("within working hours");
	}

	@Test
	@DisplayName("Changing the set passed in doesn't change the schedule")
	void daysAreCopied() {
		Set<DayOfWeek> days = EnumSet.of(MONDAY);
		WorkSchedule copy = new WorkSchedule(days, NINE_TO_FIVE, null);
		days.add(SUNDAY);

		assertThat(copy.worksOn(SUNDAY)).isFalse();
	}

	private static TimeRange range(String start, String end) {
		return new TimeRange(LocalTime.parse(start), LocalTime.parse(end));
	}

}
