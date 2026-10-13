package io.github.afunemma.salonbooking.reminder;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.github.afunemma.salonbooking.common.AppProperties;

/**
 * Sends reminders as WhatsApp template messages through Meta's Cloud API. Only active
 * with {@code app.reminders.whatsapp.enabled=true}; otherwise
 * {@link LoggingReminderSender} is used.
 * <p>
 * Businesses may only start a WhatsApp conversation with an approved template, so the
 * message text lives in Meta's template and this class sends just the values for its
 * placeholders.
 */
@Component
@ConditionalOnBooleanProperty("app.reminders.whatsapp.enabled")
class WhatsAppReminderSender implements ReminderSender {

	private static final Logger log = LoggerFactory.getLogger(WhatsAppReminderSender.class);

	private final RestClient client;

	private final AppProperties.WhatsApp settings;

	/**
	 * @param builder Spring Boot's builder, which applies the connect and read timeouts
	 * from {@code spring.http.clients.*}: reminders are sent one after another, so a
	 * hanging API must not stall the whole run
	 */
	WhatsAppReminderSender(RestClient.Builder builder, AppProperties properties) {
		this.settings = properties.reminders().whatsapp();
		this.client = builder.baseUrl(settings.apiUrl().toString())
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + settings.accessToken())
			.build();
	}

	@Override
	public void send(ReminderMessage message) {
		TemplateMessage request = new TemplateMessage("whatsapp",
				PhoneNumbers.toInternational(message.phone(), settings.defaultCountryCode()), "template",
				new Template(settings.templateName(), new Language(settings.templateLanguage()), List
					.of(new Component("body", message.templateParameters().stream().map(Parameter::text).toList()))));
		try {
			client.post()
				.uri("/{phoneNumberId}/messages", settings.phoneNumberId())
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.toBodilessEntity();
		}
		catch (RestClientResponseException ex) {
			// Meta's error body can repeat the phone number, so only the status is
			// logged.
			log.warn("WhatsApp rejected the reminder for booking {}: HTTP {}", message.bookingId(),
					ex.getStatusCode().value());
			throw ex;
		}
		log.info("WhatsApp reminder sent for booking {}", message.bookingId());
	}

	/** The JSON body Meta expects; see its "Send a message template" documentation. */
	record TemplateMessage(@JsonProperty("messaging_product") String messagingProduct, String to, String type,
			Template template) {
	}

	record Template(String name, Language language, List<Component> components) {
	}

	record Language(String code) {
	}

	record Component(String type, List<Parameter> parameters) {
	}

	record Parameter(String type, String text) {

		static Parameter text(String value) {
			return new Parameter("text", value);
		}
	}

}
