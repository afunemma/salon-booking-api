package io.github.afunemma.salonbooking.owner;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.afunemma.salonbooking.salon.SalonDtos.SalonResponse;
import io.github.afunemma.salonbooking.salon.SalonDtos.StaffRequest;
import io.github.afunemma.salonbooking.salon.SalonDtos.StaffResponse;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The staff form on the owner pages. Unlike the API, where leaving out the working days
 * means "every day", the page always shows the seven days as checkboxes, so unticking
 * them all is a mistake to point out, not a default.
 */
public record StaffForm(@NotBlank @Size(max = 255) String name, @NotNull LocalTime startsAt, @NotNull LocalTime endsAt,
		@Nullable @NotEmpty(message = "choose at least one working day") Set<DayOfWeek> workingDays,
		@Nullable LocalTime breakStartsAt, @Nullable LocalTime breakEndsAt) {

	/** A new staff member: the salon's hours, every day, no break. */
	static StaffForm forSalon(SalonResponse salon) {
		return new StaffForm("", salon.opensAt(), salon.closesAt(), EnumSet.allOf(DayOfWeek.class), null, null);
	}

	static StaffForm from(StaffResponse staff) {
		return new StaffForm(staff.name(), staff.startsAt(), staff.endsAt(), EnumSet.copyOf(staff.workingDays()),
				staff.breakStartsAt(), staff.breakEndsAt());
	}

	@AssertTrue(message = "the day must start before it ends")
	public boolean isHoursValid() {
		return startsAt == null || endsAt == null || startsAt.isBefore(endsAt);
	}

	@AssertTrue(message = "a break needs a start and an end, with the start first")
	public boolean isBreakValid() {
		if (breakStartsAt == null || breakEndsAt == null) {
			return breakStartsAt == null && breakEndsAt == null;
		}
		return breakStartsAt.isBefore(breakEndsAt);
	}

	/** Whether a day's checkbox is ticked. */
	public boolean worksOn(DayOfWeek day) {
		return workingDays != null && workingDays.contains(day);
	}

	StaffRequest toRequest() {
		return new StaffRequest(name.strip(), startsAt, endsAt, workingDays, breakStartsAt, breakEndsAt);
	}

}
