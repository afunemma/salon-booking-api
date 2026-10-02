package io.github.afunemma.salonbooking;

import static io.github.afunemma.salonbooking.AuthTestSupport.PASSWORD;
import static io.github.afunemma.salonbooking.AuthTestSupport.body;
import static io.github.afunemma.salonbooking.AuthTestSupport.postJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import io.github.afunemma.salonbooking.account.AppUserRepository;

/**
 * Proves the security rules: who can log in, which requests need a token, and that one
 * salon owner can never see or change another owner's salon.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityIntegrationTest {

	@Autowired
	private MockMvcTester mvc;

	@Autowired
	private AppUserRepository users;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Nested
	@DisplayName("Registration and login")
	class Accounts {

		@Test
		@DisplayName("Registering stores a BCrypt hash, never the password, and never returns it")
		void registerHashesPassword() {
			String email = AuthTestSupport.uniqueEmail();

			MvcTestResult result = register(email.toUpperCase(), PASSWORD);

			assertThat(result).hasStatus(HttpStatus.CREATED);
			assertThat(body(result)).doesNotContain(PASSWORD).doesNotContain("password");
			assertThat(result).bodyJson().extractingPath("$.email").isEqualTo(email);
			String storedHash = users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash();
			assertThat(storedHash).startsWith("{bcrypt}").doesNotContain(PASSWORD);
		}

		@Test
		@DisplayName("The same email can't register twice, whatever its capitalisation")
		void duplicateEmailIsRejected() {
			String email = AuthTestSupport.uniqueEmail();
			register(email, PASSWORD);

			assertThat(register(email.toUpperCase(), PASSWORD)).hasStatus(HttpStatus.CONFLICT);
		}

		@Test
		@DisplayName("Passwords must be 12 to 72 characters")
		void weakPasswordIsRejected() {
			assertThat(register(AuthTestSupport.uniqueEmail(), "short")).hasStatus(HttpStatus.BAD_REQUEST)
				.bodyJson()
				.extractingPath("$.errors.password")
				.isNotNull();
			assertThat(register(AuthTestSupport.uniqueEmail(), "x".repeat(73))).hasStatus(HttpStatus.BAD_REQUEST);
		}

		@Test
		@DisplayName("A wrong password and an unknown email give the exact same answer")
		void loginFailuresDontRevealWhichEmailsExist() {
			String email = AuthTestSupport.uniqueEmail();
			register(email, PASSWORD);

			MvcTestResult wrongPassword = login(email, "not-the-right-password");
			MvcTestResult unknownEmail = login(AuthTestSupport.uniqueEmail(), PASSWORD);

			assertThat(wrongPassword).hasStatus(HttpStatus.UNAUTHORIZED);
			assertThat(unknownEmail).hasStatus(HttpStatus.UNAUTHORIZED);
			assertThat(body(wrongPassword)).isEqualTo(body(unknownEmail));
		}

		@Test
		@DisplayName("Logging in returns a Bearer token that works on protected endpoints")
		void loginReturnsWorkingToken() {
			String email = AuthTestSupport.uniqueEmail();
			register(email, PASSWORD);

			MvcTestResult login = login(email, PASSWORD);

			assertThat(login).hasStatusOk().bodyJson().extractingPath("$.tokenType").isEqualTo("Bearer");
			String token = JsonPath.read(body(login), "$.accessToken");
			assertThat(mvc.get().uri("/api/v1/salons").header(AUTHORIZATION, "Bearer " + token)).hasStatusOk();
		}

	}

	@Nested
	@DisplayName("Tokens")
	class Tokens {

		@Test
		@DisplayName("Owner endpoints without a token return 401 with a Problem Details body")
		void missingToken() {
			MvcTestResult result = postJson(mvc, "/api/v1/salons", salonJson("No Token"));

			assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED)
				.hasHeader("WWW-Authenticate", "Bearer")
				.bodyJson()
				.extractingPath("$.title")
				.isEqualTo("Unauthorized");
		}

		@Test
		@DisplayName("A made-up token is rejected")
		void garbageToken() {
			assertThat(mvc.get().uri("/api/v1/salons").header(AUTHORIZATION, "Bearer not.a.real-token"))
				.hasStatus(HttpStatus.UNAUTHORIZED);
		}

		@Test
		@DisplayName("An expired token is rejected")
		void expiredToken() {
			Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
			String token = sign(jwtEncoder, claims(past, past.plus(1, ChronoUnit.HOURS)));

			assertThat(mvc.get().uri("/api/v1/salons").header(AUTHORIZATION, "Bearer " + token))
				.hasStatus(HttpStatus.UNAUTHORIZED);
		}

		@Test
		@DisplayName("A token signed with someone else's key is rejected, even if it looks valid")
		void forgedToken() {
			byte[] attackerKey = new byte[32];
			new SecureRandom().nextBytes(attackerKey);
			JwtEncoder attacker = new NimbusJwtEncoder(
					new ImmutableSecret<>(new SecretKeySpec(attackerKey, "HmacSHA256")));
			Instant now = Instant.now();
			String token = sign(attacker, claims(now, now.plus(1, ChronoUnit.HOURS)));

			assertThat(mvc.get().uri("/api/v1/salons").header(AUTHORIZATION, "Bearer " + token))
				.hasStatus(HttpStatus.UNAUTHORIZED);
		}

		private static JwtClaimsSet claims(Instant issuedAt, Instant expiresAt) {
			return JwtClaimsSet.builder()
				.issuer("salon-booking-api")
				.subject("1")
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.build();
		}

		private static String sign(JwtEncoder encoder, JwtClaimsSet claims) {
			JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
			return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		}

	}

	@Nested
	@DisplayName("Owners can only manage their own salons")
	class Ownership {

		private String alice;

		private String bob;

		private long alicesSalon;

		private long alicesBooking;

		@BeforeEach
		void aliceHasASalonWithABooking() {
			alice = AuthTestSupport.registerAndLogin(mvc);
			bob = AuthTestSupport.registerAndLogin(mvc);
			alicesSalon = idOf(asUser(alice, "/api/v1/salons", salonJson("Alice's Salon")));
			long service = idOf(asUser(alice, "/api/v1/salons/" + alicesSalon + "/services", """
					{"name": "Braids", "durationMinutes": 60}
					"""));
			alicesBooking = idOf(postJson(mvc, "/api/v1/salons/" + alicesSalon + "/bookings", """
					{"serviceId": %d, "clientName": "Lerato", "clientPhone": "082 555 1234",
					 "date": "2030-01-07", "startTime": "10:00"}
					""".formatted(service)));
		}

		@Test
		@DisplayName("Bob can't see Alice's day view (client names and phone numbers)")
		void cannotReadOtherOwnersBookings() {
			assertThat(mvc.get()
				.uri("/api/v1/salons/{id}/bookings?date=2030-01-07", alicesSalon)
				.header(AUTHORIZATION, bob)).hasStatus(HttpStatus.FORBIDDEN)
				.bodyJson()
				.extractingPath("$.title")
				.isEqualTo("Forbidden");
			assertThat(mvc.get()
				.uri("/api/v1/salons/{id}/bookings?date=2030-01-07", alicesSalon)
				.header(AUTHORIZATION, alice)).hasStatusOk();
		}

		@Test
		@DisplayName("Bob can't cancel, complete or no-show Alice's bookings")
		void cannotChangeOtherOwnersBookings() {
			for (String action : new String[] { "cancel", "complete", "no-show" }) {
				assertThat(mvc.post()
					.uri("/api/v1/salons/{salon}/bookings/{booking}/{action}", alicesSalon, alicesBooking, action)
					.header(AUTHORIZATION, bob)).as(action).hasStatus(HttpStatus.FORBIDDEN);
			}
			assertThat(mvc.post()
				.uri("/api/v1/salons/{salon}/bookings/{booking}/cancel", alicesSalon, alicesBooking)
				.header(AUTHORIZATION, alice)).hasStatusOk();
		}

		@Test
		@DisplayName("Bob can't add services to Alice's salon")
		void cannotAddServicesToOtherOwnersSalon() {
			assertThat(asUser(bob, "/api/v1/salons/" + alicesSalon + "/services", """
					{"name": "Fake service", "durationMinutes": 30}
					""")).hasStatus(HttpStatus.FORBIDDEN);
		}

		@Test
		@DisplayName("Each owner's salon list only shows their own salons")
		void salonListIsPerOwner() {
			assertThat(mvc.get().uri("/api/v1/salons").header(AUTHORIZATION, alice)).hasStatusOk()
				.bodyJson()
				.extractingPath("$[*].name")
				.asArray()
				.containsExactly("Alice's Salon");
			assertThat(mvc.get().uri("/api/v1/salons").header(AUTHORIZATION, bob)).hasStatusOk()
				.bodyJson()
				.extractingPath("$")
				.asArray()
				.isEmpty();
		}

		private MvcTestResult asUser(String authorization, String uri, String json) {
			return mvc.post()
				.uri(uri)
				.header(AUTHORIZATION, authorization)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json)
				.exchange();
		}

		private static long idOf(MvcTestResult result) {
			assertThat(result).hasStatus(HttpStatus.CREATED);
			String location = result.getResponse().getHeader("Location");
			return Long.parseLong(location.substring(location.lastIndexOf('/') + 1));
		}

	}

	private MvcTestResult register(String email, String password) {
		return postJson(mvc, "/api/v1/auth/register", """
				{"email": "%s", "password": "%s"}
				""".formatted(email, password));
	}

	private MvcTestResult login(String email, String password) {
		return postJson(mvc, "/api/v1/auth/login", """
				{"email": "%s", "password": "%s"}
				""".formatted(email, password));
	}

	private static String salonJson(String name) {
		return """
				{"name": "%s", "opensAt": "09:00", "closesAt": "17:00"}
				""".formatted(name);
	}

}
