package io.github.afunemma.salonbooking.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.ResourceAccessException;

import com.sun.net.httpserver.HttpServer;

import io.github.afunemma.salonbooking.TestcontainersConfiguration;

/**
 * With WhatsApp switched on, the app uses {@link WhatsAppReminderSender}, and Spring
 * Boot's HTTP timeout settings really apply to it. A local server that never answers in
 * time stands in for a hanging Meta API.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class WhatsAppSwitchIntegrationTest {

	private static final HttpServer slowServer = startSlowServer();

	@Autowired
	private ReminderSender sender;

	@DynamicPropertySource
	static void switchOnWhatsApp(DynamicPropertyRegistry registry) {
		registry.add("app.reminders.whatsapp.enabled", () -> "true");
		registry.add("app.reminders.whatsapp.api-url",
				() -> "http://localhost:" + slowServer.getAddress().getPort() + "/v23.0");
		registry.add("app.reminders.whatsapp.phone-number-id", () -> "1234567890");
		registry.add("app.reminders.whatsapp.access-token", () -> "test-token");
		registry.add("spring.http.clients.read-timeout", () -> "500ms");
	}

	@AfterAll
	static void stopServer() {
		slowServer.stop(0);
	}

	@Test
	@DisplayName("Switching WhatsApp on replaces the logging sender")
	void usesWhatsAppSender() {
		assertThat(sender).isInstanceOf(WhatsAppReminderSender.class);
	}

	@Test
	@DisplayName("A hanging API times out quickly instead of stalling the reminder run")
	void hangingApiTimesOut() {
		ReminderMessage reminder = new ReminderMessage(42L, "082 123 4567", "Thabo", "Haircut", "Sipho's Cuts",
				LocalDate.of(2030, 1, 7), LocalTime.of(10, 0), "https://salon.example.com/cancel");
		long start = System.nanoTime();

		assertThatThrownBy(() -> sender.send(reminder)).isInstanceOf(ResourceAccessException.class);
		assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(3));
	}

	/** Accepts requests but waits 5 seconds before answering. */
	private static HttpServer startSlowServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
			server.createContext("/", exchange -> {
				try {
					Thread.sleep(5000);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
				exchange.sendResponseHeaders(200, -1);
				exchange.close();
			});
			server.start();
			return server;
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
