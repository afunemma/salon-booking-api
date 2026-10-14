package io.github.afunemma.salonbooking.salon;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.github.afunemma.salonbooking.scheduling.TimeRange;
import io.github.afunemma.salonbooking.scheduling.WorkSchedule;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies for the salon endpoints.
 * <p>
 * The API uses these records instead of exposing JPA entities directly, so the database
 * structure can change without breaking API clients.
 */
public final class SalonDtos {

	private SalonDtos() {
	}

	public record CreateSalonRequest(@NotBlank @Size(max = 255) String name, @NotNull LocalTime opensAt,
			@NotNull LocalTime closesAt) {

		// Fields can still be null here: this check runs alongside @NotNull, not after
		// it.
		@JsonIgnore
		@AssertTrue(message = "opensAt must be before closesAt")
		boolean isOpeningHoursValid() {
			return opensAt == null || closesAt == null || opensAt.isBefore(closesAt);
		}
	}

	public record SalonResponse(Long id, String name, LocalTime opensAt, LocalTime closesAt) {

		static SalonResponse from(Salon salon) {
			return new SalonResponse(salon.getId(), salon.getName(), salon.getOpeningHours().open(),
					salon.getOpeningHours().close());
		}
	}

	/**
	 * A staff member to add, or to replace (PUT) as a whole. Left-out fields get their
	 * defaults: the salon's opening and closing time, every day of the week, no break.
	 *
	 * @param workingDays e.g. ["TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"]
	 * @param breakStartsAt start of a daily break, e.g. lunch; give both ends or neither
	 */
	public record StaffRequest(@NotBlank @Size(max = 255) String name, @Nullable LocalTime startsAt,
			@Nullable LocalTime endsAt,
			@Nullable @Size(min = 1, message = "must contain at least one day") Set<DayOfWeek> workingDays,
			@Nullable LocalTime breakStartsAt, @Nullable LocalTime breakEndsAt) {

		/** Just a name and hours: every day, no break. */
		public StaffRequest(String name, @Nullable LocalTime startsAt, @Nullable LocalTime endsAt) {
			this(name, startsAt, endsAt, null, null, null);
		}

		@JsonIgnore
		@AssertTrue(message = "startsAt must be before endsAt")
		boolean isWorkingHoursValid() {
			return startsAt == null || endsAt == null || startsAt.isBefore(endsAt);
		}

		@JsonIgnore
		@AssertTrue(message = "a break needs both breakStartsAt and breakEndsAt, with the start first")
		boolean isBreakValid() {
			if (breakStartsAt == null || breakEndsAt == null) {
				return breakStartsAt == null && breakEndsAt == null;
			}
			return breakStartsAt.isBefore(breakEndsAt);
		}
	}

	public record StaffResponse(Long id, String name, LocalTime startsAt, LocalTime endsAt, List<DayOfWeek> workingDays,
			@Nullable LocalTime breakStartsAt, @Nullable LocalTime breakEndsAt) {

		static StaffResponse from(StaffMember staff) {
			WorkSchedule schedule = staff.getSchedule();
			TimeRange breakTime = schedule.breakTime();
			return new StaffResponse(staff.getId(), staff.getName(), schedule.hours().open(), schedule.hours().close(),
					schedule.days().stream().sorted().toList(), breakTime != null ? breakTime.start() : null,
					breakTime != null ? breakTime.end() : null);
		}
	}

	/**
	 * @param staffId who is away; leave it out to close the whole salon
	 * @param firstDay first day off
	 * @param lastDay last day off, included; the same as firstDay for a single day
	 */
	public record TimeOffRequest(@Nullable Long staffId, @NotNull LocalDate firstDay, @NotNull LocalDate lastDay) {

		@JsonIgnore
		@AssertTrue(message = "lastDay must not be before firstDay")
		boolean isRangeValid() {
			return firstDay == null || lastDay == null || !lastDay.isBefore(firstDay);
		}
	}

	/**
	 * @param staffId {@code null} when the whole salon is closed
	 */
	public record TimeOffResponse(Long id, @Nullable Long staffId, @Nullable String staffName, LocalDate firstDay,
			LocalDate lastDay) {

		static TimeOffResponse from(TimeOff timeOff) {
			StaffMember staff = timeOff.getStaff();
			return new TimeOffResponse(timeOff.getId(), staff != null ? staff.getId() : null,
					staff != null ? staff.getName() : null, timeOff.getFirstDay(), timeOff.getLastDay());
		}
	}

	public record ServiceRequest(@NotBlank @Size(max = 255) String name,
			@NotNull @Positive @Max(value = 720, message = "must be at most 12 hours") Integer durationMinutes,
			@PositiveOrZero @Nullable Integer priceFromCents, @PositiveOrZero @Nullable Integer priceToCents) {

		@JsonIgnore
		@AssertTrue(message = "priceToCents must not be lower than priceFromCents")
		boolean isPriceRangeValid() {
			return priceFromCents == null || priceToCents == null || priceToCents >= priceFromCents;
		}
	}

	public record ServiceResponse(Long id, String name, int durationMinutes, @Nullable Integer priceFromCents,
			@Nullable Integer priceToCents) {

		static ServiceResponse from(ServiceOffering service) {
			return new ServiceResponse(service.getId(), service.getName(),
					Math.toIntExact(service.getDuration().toMinutes()), service.getPriceFromCents(),
					service.getPriceToCents());
		}
	}

}
