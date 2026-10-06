package io.github.afunemma.salonbooking.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BookingReminderTest {

	private static final Instant FIRST_TRY = Instant.parse("2030-01-06T16:00:00Z");

	private static final Instant SECOND_TRY = Instant.parse("2030-01-06T17:00:00Z");

	@Test
	@DisplayName("A failed attempt is recorded without a sent time, and a later success fills it in")
	void failedThenSent() {
		BookingReminder reminder = new BookingReminder(1L, FIRST_TRY, false);
		assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.FAILED);
		assertThat(reminder.getSentAt()).isNull();

		reminder.recordRetry(SECOND_TRY, true);

		assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.SENT);
		assertThat(reminder.getAttempts()).isEqualTo(2);
		assertThat(reminder.getLastAttemptAt()).isEqualTo(SECOND_TRY);
		assertThat(reminder.getSentAt()).isEqualTo(SECOND_TRY);
	}

	@Test
	@DisplayName("A reminder that was already sent can't be retried, so the client isn't messaged twice")
	void sentReminderCannotBeRetried() {
		BookingReminder reminder = new BookingReminder(1L, FIRST_TRY, true);

		assertThatThrownBy(() -> reminder.recordRetry(SECOND_TRY, true)).isInstanceOf(IllegalStateException.class);
	}

}
