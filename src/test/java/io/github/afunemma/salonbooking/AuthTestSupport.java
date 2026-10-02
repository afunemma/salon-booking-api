package io.github.afunemma.salonbooking;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

/**
 * Helpers for API tests that need a logged-in salon owner.
 */
public final class AuthTestSupport {

	public static final String PASSWORD = "correct-horse-battery";

	private AuthTestSupport() {
	}

	/** A unique email, so tests never clash with each other's accounts. */
	public static String uniqueEmail() {
		return "owner-" + UUID.randomUUID() + "@example.com";
	}

	/**
	 * Registers a new owner and logs in. Returns the value for the Authorization header.
	 */
	public static String registerAndLogin(MockMvcTester mvc) {
		String email = uniqueEmail();
		assertThat(postJson(mvc, "/api/v1/auth/register", """
				{"email": "%s", "password": "%s"}
				""".formatted(email, PASSWORD))).hasStatus(HttpStatus.CREATED);
		MvcTestResult login = postJson(mvc, "/api/v1/auth/login", """
				{"email": "%s", "password": "%s"}
				""".formatted(email, PASSWORD));
		assertThat(login).hasStatusOk();
		return "Bearer " + JsonPath.read(body(login), "$.accessToken");
	}

	public static MvcTestResult postJson(MockMvcTester mvc, String uri, String json) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	public static String body(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
