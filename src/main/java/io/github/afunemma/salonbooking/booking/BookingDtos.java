package io.github.afunemma.salonbooking.booking;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies for the booking endpoints.
 */
public final class BookingDtos {

	private BookingDtos() {
	}

	public record CreateBookingRequest(@NotNull Long serviceId, @NotBlank @Size(max = 255) String clientName,
			@NotBlank @Pattern(regexp = "\\+?[0-9 ]{7,20}",
					message = "must be a phone number, e.g. 082 123 4567") String clientPhone,
			@NotNull LocalDate date, @NotNull LocalTime startTime) {
	}

	/**
	 * @param cancelUrl the client's own cancel link. Only included in the response to the
	 * client who just booked; the owner's day view leaves it out.
	 */
	public record BookingResponse(Long id, Long serviceId, String serviceName, String clientName, String clientPhone,
			LocalDate date, LocalTime startTime, LocalTime endTime, BookingStatus status,
			@JsonInclude(JsonInclude.Include.NON_NULL) @Nullable String cancelUrl) {

		static BookingResponse from(Booking booking) {
			return withCancelUrl(booking, null);
		}

		static BookingResponse withCancelUrl(Booking booking, @Nullable String cancelUrl) {
			return new BookingResponse(booking.getId(), booking.getService().getId(), booking.getService().getName(),
					booking.getClientName(), booking.getClientPhone(), booking.getBookingDate(), booking.getStartTime(),
					booking.getEndTime(), booking.getStatus(), cancelUrl);
		}
	}

	/**
	 * How often clients didn't arrive, over a date range.
	 *
	 * @param completed appointments the owner marked as done
	 * @param noShows appointments the client didn't arrive for
	 * @param cancelled cancelled by the client or the salon; not counted in the rate,
	 * because the salon was told in time
	 * @param notMarked past bookings still marked as booked: the owner hasn't recorded
	 * what happened, so the rate may be incomplete
	 * @param noShowRatePercent no-shows ÷ (completed + no-shows), as a percentage with
	 * one decimal; {@code null} when there is nothing to divide by
	 */
	public record NoShowStatsResponse(LocalDate from, LocalDate to, long completed, long noShows, long cancelled,
			long notMarked, @Nullable Double noShowRatePercent) {

		static NoShowStatsResponse of(LocalDate from, LocalDate to, long completed, long noShows, long cancelled,
				long notMarked) {
			return new NoShowStatsResponse(from, to, completed, noShows, cancelled, notMarked,
					noShowRatePercent(completed, noShows));
		}

		static @Nullable Double noShowRatePercent(long completed, long noShows) {
			long appointments = completed + noShows;
			if (appointments == 0) {
				return null;
			}
			return Math.round(noShows * 1000.0 / appointments) / 10.0;
		}
	}

	public record FreeSlotsResponse(LocalDate date, Long serviceId, int durationMinutes, List<LocalTime> startTimes) {
	}

}
