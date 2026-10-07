package io.github.afunemma.salonbooking.booking;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What a client sees on their cancel page. Leaves out the phone number: only the name,
 * which the client typed themselves, and the appointment.
 *
 * @param state whether the client can still cancel
 */
public record ClientBookingView(Long bookingId, String token, String clientName, String serviceName, String salonName,
		LocalDate date, LocalTime startTime, State state) {

	public enum State {

		/** Active and in the future: the cancel button is shown. */
		CAN_CANCEL,

		/** Already cancelled, by the client or the salon. */
		CANCELLED,

		/** Started, done or marked as a no-show: too late to cancel online. */
		TOO_LATE

	}

	public boolean canCancel() {
		return state == State.CAN_CANCEL;
	}

	public boolean isCancelled() {
		return state == State.CANCELLED;
	}

}
