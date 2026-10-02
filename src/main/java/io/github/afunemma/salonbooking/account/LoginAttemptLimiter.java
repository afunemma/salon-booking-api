package io.github.afunemma.salonbooking.account;

import java.time.Duration;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.afunemma.salonbooking.common.AppProperties;
import io.github.afunemma.salonbooking.common.AppProperties.RateLimit;
import io.github.afunemma.salonbooking.common.TooManyRequestsException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;

/**
 * Slows down password guessing with two token-bucket limits:
 * <ul>
 * <li><b>Per IP address, all attempts:</b> stops one machine from trying many accounts
 * quickly.</li>
 * <li><b>Per email, failed attempts only:</b> stops many guesses at one account's
 * password. Only failures count, and a successful login resets the count, so the real
 * owner isn't locked out by their own typos once they get it right.</li>
 * </ul>
 * Buckets live in memory and expire when unused, so memory stays bounded. With several
 * app instances each keeps its own counts; a shared store (e.g. Redis) would be needed
 * for exact limits across instances.
 */
@Component
class LoginAttemptLimiter {

	private static final long MAX_TRACKED_KEYS = 100_000;

	private final RateLimit ipLimit;

	private final RateLimit emailLimit;

	private final Cache<String, Bucket> attemptsPerIp;

	private final Cache<String, Bucket> failuresPerEmail;

	LoginAttemptLimiter(AppProperties properties) {
		this.ipLimit = properties.security().loginAttemptsPerIp();
		this.emailLimit = properties.security().failedLoginsPerEmail();
		this.attemptsPerIp = newCache(ipLimit.window());
		this.failuresPerEmail = newCache(emailLimit.window());
	}

	/**
	 * Counts this attempt against the IP limit, and checks the email isn't paused.
	 * @throws TooManyRequestsException if either limit is reached
	 */
	void checkAllowed(String clientIp, String email) {
		ConsumptionProbe ipProbe = attemptsPerIp.get(clientIp, key -> newBucket(ipLimit))
			.tryConsumeAndReturnRemaining(1);
		if (!ipProbe.isConsumed()) {
			throw tooManyAttempts(ipProbe.getNanosToWaitForRefill());
		}
		Bucket emailFailures = failuresPerEmail.getIfPresent(key(email));
		if (emailFailures != null) {
			EstimationProbe emailProbe = emailFailures.estimateAbilityToConsume(1);
			if (!emailProbe.canBeConsumed()) {
				throw tooManyAttempts(emailProbe.getNanosToWaitForRefill());
			}
		}
	}

	void recordFailure(String email) {
		failuresPerEmail.get(key(email), k -> newBucket(emailLimit)).tryConsume(1);
	}

	void recordSuccess(String email) {
		failuresPerEmail.invalidate(key(email));
	}

	private static String key(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	private static Bucket newBucket(RateLimit limit) {
		return Bucket.builder()
			.addLimit(bandwidth -> bandwidth.capacity(limit.attempts()).refillGreedy(limit.attempts(), limit.window()))
			.build();
	}

	private static Cache<String, Bucket> newCache(Duration window) {
		return Caffeine.newBuilder().expireAfterAccess(window).maximumSize(MAX_TRACKED_KEYS).build();
	}

	private static TooManyRequestsException tooManyAttempts(long nanosToWait) {
		// Round up so clients never retry a moment too early.
		Duration retryAfter = Duration.ofSeconds(Math.max(1, (nanosToWait + 999_999_999) / 1_000_000_000));
		return new TooManyRequestsException("Too many login attempts. Try again later.", retryAfter);
	}

}
