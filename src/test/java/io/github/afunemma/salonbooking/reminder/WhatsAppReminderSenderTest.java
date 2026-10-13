package io.github.afunemma.salonbooking.reminder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import io.github.afunemma.salonbooking.common.AppProperties;

/**
 * Checks the exact request sent to Meta's API, against a fake server: tests never contact
 * Meta or send real messages.
 */
class WhatsAppReminderSenderTest {

	private static final ReminderMessage REMINDER = new ReminderMessage(42L, "082 123 4567", "Thabo", "Haircut",
			"Sipho's Cuts", LocalDate.of(2030, 1, 7), LocalTime.of(10, 0),
			"https://salon.example.com/bookings/42/cancel?token=abc");

	private MockRestServiceServer meta;

	private WhatsAppReminderSender sender;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		meta = MockRestServiceServer.bindTo(builder).build();
		sender = new WhatsAppReminderSender(builder, properties());
	}

	@Test
	@DisplayName("Sends the approved template to the client's number, with the booking details as its parameters")
	void sendsTemplateMessage() {
		meta.expect(requestTo("https://graph.example.test/v23.0/1234567890/messages"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer test-token"))
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.messaging_product").value("whatsapp"))
			.andExpect(jsonPath("$.to").value("27821234567"))
			.andExpect(jsonPath("$.type").value("template"))
			.andExpect(jsonPath("$.template.name").value("appointment_reminder"))
			.andExpect(jsonPath("$.template.language.code").value("en"))
			.andExpect(jsonPath("$.template.components[0].type").value("body"))
			.andExpect(jsonPath("$.template.components[0].parameters[*].text")
				.value(org.hamcrest.Matchers.contains("Thabo", "Haircut", "Sipho's Cuts", "Monday 7 January", "10:00",
						"https://salon.example.com/bookings/42/cancel?token=abc")))
			.andRespond(withSuccess("""
					{"messaging_product": "whatsapp", "messages": [{"id": "wamid.test"}]}
					""", MediaType.APPLICATION_JSON));

		sender.send(REMINDER);

		meta.verify();
	}

	@Test
	@DisplayName("A rejected message throws, so the reminder job records it as failed and retries it")
	void rejectedMessageThrows() {
		meta.expect(requestTo("https://graph.example.test/v23.0/1234567890/messages")).andRespond(withBadRequest());

		assertThatThrownBy(() -> sender.send(REMINDER)).isInstanceOf(RestClientResponseException.class);
	}

	static AppProperties properties() {
		return new AppProperties(ZoneId.of("Africa/Johannesburg"), URI.create("https://salon.example.com"),
				new AppProperties.Booking(Duration.ofMinutes(15)),
				new AppProperties.Reminders("-",
						new AppProperties.WhatsApp(true, URI.create("https://graph.example.test/v23.0"), "1234567890",
								"test-token", "appointment_reminder", "en", "27")),
				new AppProperties.Security(null, null, Duration.ofHours(1),
						new AppProperties.RateLimit(20, Duration.ofMinutes(1)),
						new AppProperties.RateLimit(5, Duration.ofMinutes(15))));
	}

}
