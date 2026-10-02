package io.github.afunemma.salonbooking.common;

import java.time.Duration;

/**
 * The caller has made too many requests and must wait. Mapped to HTTP 429 with a
 * {@code Retry-After} header.
 */
public class TooManyRequestsException extends RuntimeException {

	private final Duration retryAfter;

	public TooManyRequestsException(String message, Duration retryAfter) {
		super(message);
		this.retryAfter = retryAfter;
	}

	public Duration getRetryAfter() {
		return retryAfter;
	}

}
