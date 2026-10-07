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

	public record FreeSlotsResponse(LocalDate date, Long serviceId, int durationMinutes, List<LocalTime> startTimes) {
	}

}
