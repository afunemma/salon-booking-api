package io.github.afunemma.salonbooking.common;

import java.time.Duration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.afunemma.salonbooking.common.AppProperties.RateLimit;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;

/**
 * A token-bucket limit per key, e.g. "20 booking attempts per IP address per hour". Used
 * for logins and bookings.
 * <p>
 * Each key gets its own bucket that refills steadily over the window. Buckets live in
 * memory, expire when unused, and at most 100,000 keys are tracked, so memory stays
 * bounded even under attack. With several app instances each keeps its own counts; a
 * shared store (e.g. Redis) would be needed for exact limits across instances.
 */
public final class KeyedRateLimiter {

	private static final long MAX_TRACKED_KEYS = 100_000;

	private final RateLimit limit;

	private final String message;

	private final Cache<String, Bucket> buckets;

	/**
	 * @param message what the client is told when the limit is reached
	 */
	public KeyedRateLimiter(RateLimit limit, String message) {
		this.limit = limit;
		this.message = message;
		this.buckets = Caffeine.newBuilder().expireAfterAccess(limit.window()).maximumSize(MAX_TRACKED_KEYS).build();
	}

	/**
	 * Counts one attempt for the key.
	 * @throws TooManyRequestsException if the key has used up its limit
	 */
	public void consume(String key) {
		ConsumptionProbe probe = buckets.get(key, k -> newBucket()).tryConsumeAndReturnRemaining(1);
		if (!probe.isConsumed()) {
			throw tooMany(probe.getNanosToWaitForRefill());
		}
	}

	/**
	 * Checks the key still has room, without counting anything.
	 * @throws TooManyRequestsException if the key has used up its limit
	 */
	public void check(String key) {
		Bucket bucket = buckets.getIfPresent(key);
		if (bucket != null) {
			EstimationProbe probe = bucket.estimateAbilityToConsume(1);
			if (!probe.canBeConsumed()) {
				throw tooMany(probe.getNanosToWaitForRefill());
			}
		}
	}

	/** Counts one attempt without failing, e.g. a failed login. */
	public void record(String key) {
		buckets.get(key, k -> newBucket()).tryConsume(1);
	}

	/** Forgets the key's count, e.g. after a successful login. */
	public void reset(String key) {
		buckets.invalidate(key);
	}

	private Bucket newBucket() {
		return Bucket.builder()
			.addLimit(bandwidth -> bandwidth.capacity(limit.attempts()).refillGreedy(limit.attempts(), limit.window()))
			.build();
	}

	private TooManyRequestsException tooMany(long nanosToWait) {
		// Round up so clients never retry a moment too early.
		Duration retryAfter = Duration.ofSeconds(Math.max(1, (nanosToWait + 999_999_999) / 1_000_000_000));
		return new TooManyRequestsException(message, retryAfter);
	}

}
