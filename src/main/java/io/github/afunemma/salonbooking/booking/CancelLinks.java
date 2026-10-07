package io.github.afunemma.salonbooking.booking;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import io.github.afunemma.salonbooking.common.AppProperties;

/**
 * Builds and checks the links clients use to cancel their own booking, e.g.
 * {@code https://…/bookings/42/cancel?token=Xk3…}.
 * <p>
 * The token is an HMAC-SHA256 signature of the booking id, made with a secret key that
 * only the server knows. Changing the id in a link breaks the signature, so a client can
 * only cancel their own booking. Nothing is stored: the token is recomputed whenever it
 * is needed or checked. A link stops working once the booking is no longer active, so it
 * can only ever be used once.
 */
@Component
public class CancelLinks {

	private static final Logger log = LoggerFactory.getLogger(CancelLinks.class);

	private static final String ALGORITHM = "HmacSHA256";

	/**
	 * 16 bytes (128 bits) of the 32-byte signature: impossible to guess, and keeps the
	 * link short enough for an SMS.
	 */
	private static final int TOKEN_BYTES = 16;

	private final SecretKeySpec key;

	private final String baseUrl;

	CancelLinks(AppProperties properties) {
		this.key = new SecretKeySpec(keyBytes(properties.security().cancelLinkSecret()), ALGORITHM);
		this.baseUrl = properties.publicBaseUrl().toString().replaceAll("/+$", "");
	}

	/** The full link to put in a message to the client. */
	public String urlFor(Long bookingId) {
		return baseUrl + "/bookings/" + bookingId + "/cancel?token=" + tokenFor(bookingId);
	}

	String tokenFor(Long bookingId) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(signature(bookingId));
	}

	/**
	 * Compares in constant time, so response times don't reveal how much of a guessed
	 * token was right.
	 */
	boolean isValid(Long bookingId, String token) {
		byte[] given;
		try {
			given = Base64.getUrlDecoder().decode(token);
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
		return MessageDigest.isEqual(given, signature(bookingId));
	}

	private byte[] signature(Long bookingId) {
		try {
			// A Mac object isn't thread-safe, so each call gets its own.
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(key);
			byte[] full = mac.doFinal(("cancel-booking:" + bookingId).getBytes(StandardCharsets.UTF_8));
			return Arrays.copyOf(full, TOKEN_BYTES);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("HmacSHA256 is always available in Java", ex);
		}
	}

	private static byte[] keyBytes(@Nullable String secret) {
		if (secret != null) {
			return secret.getBytes(StandardCharsets.UTF_8);
		}
		log.warn("No app.security.cancel-link-secret configured: using a random key. "
				+ "Cancel links will stop working when the app restarts. Set APP_SECURITY_CANCEL_LINK_SECRET in production.");
		byte[] randomKey = new byte[32];
		new SecureRandom().nextBytes(randomKey);
		return randomKey;
	}

}
