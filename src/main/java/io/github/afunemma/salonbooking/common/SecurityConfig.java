package io.github.afunemma.salonbooking.common;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Who may call what.
 * <ul>
 * <li><b>Anyone:</b> register, log in, view a salon and its services, see free slots, and
 * book, and cancel their own booking from the signed link in their reminder. Clients
 * don't need an account.</li>
 * <li><b>Logged-in owners:</b> everything else, such as creating salons, adding services,
 * the day view and changing bookings. Whether the user owns that particular salon is
 * checked in the service layer.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JsonMapper jsonMapper,
			@Value("${management.server.port:-1}") int managementPort) throws Exception {
		http
			// CSRF protection is off on purpose. CSRF tricks a browser into
			// sending a request with cookies it attaches automatically.
			// This API uses no cookies or sessions: the token travels in the
			// Authorization header, which browsers never add on their own.
			// If cookie-based login is ever added, turn CSRF back on.
			// See ADR-0006. CodeQL flags this line; the alert is dismissed
			// as a false positive.
			.csrf(csrf -> csrf.disable())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(requests -> requests
				// The internal management port (metrics, health) is only reachable from
				// inside the network and must never be published to the internet.
				.requestMatchers(request -> request.getLocalPort() == managementPort)
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login")
				.permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/salons/*", "/api/v1/salons/*/services",
						"/api/v1/salons/*/free-slots")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/salons/*/bookings")
				.permitAll()
				// The client's cancel page. The signed token in the link is the
				// permission, checked in BookingService.
				.requestMatchers("/bookings/*/cancel")
				.permitAll()
				.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/actuator/health/**",
						"/livez", "/readyz")
				.permitAll()
				.anyRequest()
				.authenticated())
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults())
				.authenticationEntryPoint((request, response, ex) -> writeProblem(response, jsonMapper,
						HttpStatus.UNAUTHORIZED, "Unauthorized", "A valid login token is required")))
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint((request, response, ex) -> writeProblem(response, jsonMapper,
						HttpStatus.UNAUTHORIZED, "Unauthorized", "A valid login token is required"))
				.accessDeniedHandler((request, response, ex) -> writeProblem(response, jsonMapper, HttpStatus.FORBIDDEN,
						"Forbidden", "You don't have access to this resource")));
		return http.build();
	}

	/**
	 * BCrypt by default, with the algorithm stored in the hash so it can be upgraded
	 * later.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	/**
	 * Security errors happen before the controllers, so they are written here in the same
	 * Problem Details format.
	 */
	private static void writeProblem(HttpServletResponse response, JsonMapper jsonMapper, HttpStatus status,
			String title, String detail) throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		if (status == HttpStatus.UNAUTHORIZED) {
			response.setHeader("WWW-Authenticate", "Bearer");
		}
		jsonMapper.writeValue(response.getOutputStream(), problem);
	}

}
