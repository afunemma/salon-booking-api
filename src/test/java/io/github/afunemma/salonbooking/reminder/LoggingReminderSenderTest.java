package io.github.afunemma.salonbooking.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LoggingReminderSenderTest {

	@Test
	@DisplayName("Only the last three digits of a phone number are logged")
	void logsOnlyLastThreeDigits() {
		assertThat(LoggingReminderSender.lastDigits("082 123 4567")).isEqualTo("567");
		assertThat(LoggingReminderSender.lastDigits("+27 82 123 4567")).isEqualTo("567");
		assertThat(LoggingReminderSender.lastDigits("12")).isEqualTo("12");
	}

	@Test
	@DisplayName("Sending through the logging sender never fails")
	void sendDoesNotThrow() {
		new LoggingReminderSender().send(new ReminderMessage(1L, "082 123 4567", "Hi"));
	}

}
