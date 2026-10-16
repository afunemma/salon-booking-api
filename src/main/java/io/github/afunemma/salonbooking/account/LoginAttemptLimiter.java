package io.github.afunemma.salonbooking.account;

import java.util.Locale;

import org.springframework.stereotype.Component;

import io.github.afunemma.salonbooking.common.AppProperties;
import io.github.afunemma.salonbooking.common.KeyedRateLimiter;
import io.github.afunemma.salonbooking.common.TooManyRequestsException;

/**
 * Slows down password guessing with two token-bucket limits:
 * <ul>
 * <li><b>Per IP address, all attempts:</b> stops one machine from trying many accounts
 * quickly.</li>
 * <li><b>Per email, failed attempts only:</b> stops many guesses at one account's
 * password. Only failures count, and a successful login resets the count, so the real
 * owner isn't locked out by their own typos once they get it right.</li>
 * </ul>
 * Both use {@link KeyedRateLimiter}, which keeps the counts in memory.
 */
@Component
class LoginAttemptLimiter {

	private static final String MESSAGE = "Too many login attempts. Try again later.";

	private final KeyedRateLimiter attemptsPerIp;

	private final KeyedRateLimiter failuresPerEmail;

	LoginAttemptLimiter(AppProperties properties) {
		this.attemptsPerIp = new KeyedRateLimiter(properties.security().loginAttemptsPerIp(), MESSAGE);
		this.failuresPerEmail = new KeyedRateLimiter(properties.security().failedLoginsPerEmail(), MESSAGE);
	}

	/**
	 * Counts this attempt against the IP limit, and checks the email isn't paused.
	 * @throws TooManyRequestsException if either limit is reached
	 */
	void checkAllowed(String clientIp, String email) {
		attemptsPerIp.consume(clientIp);
		failuresPerEmail.check(key(email));
	}

	void recordFailure(String email) {
		failuresPerEmail.record(key(email));
	}

	void recordSuccess(String email) {
		failuresPerEmail.reset(key(email));
	}

	private static String key(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
