package io.github.afunemma.salonbooking.account;

import java.time.Clock;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.afunemma.salonbooking.account.AuthDtos.LoginRequest;
import io.github.afunemma.salonbooking.account.AuthDtos.RegisterRequest;
import io.github.afunemma.salonbooking.account.AuthDtos.TokenResponse;
import io.github.afunemma.salonbooking.account.AuthDtos.UserResponse;
import io.github.afunemma.salonbooking.common.TokenService;
import io.github.afunemma.salonbooking.common.TokenService.IssuedToken;
import io.github.afunemma.salonbooking.common.TooManyRequestsException;
import io.micrometer.core.instrument.MeterRegistry;

@Service
@Transactional(readOnly = true)
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokenService;

	private final Clock clock;

	private final LoginAttemptLimiter limiter;

	private final MeterRegistry meters;

	/**
	 * Checked against when the email doesn't exist, so a failed login takes the same time
	 * whether or not the account exists. Otherwise response times would reveal which
	 * emails are registered.
	 */
	private final String dummyHash;

	AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService, Clock clock,
			LoginAttemptLimiter limiter, MeterRegistry meters) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.clock = clock;
		this.limiter = limiter;
		this.meters = meters;
		this.dummyHash = passwordEncoder.encode("timing-attack-protection-dummy");
	}

	@Transactional
	public UserResponse register(RegisterRequest request) {
		String email = normalise(request.email());
		if (users.existsByEmailIgnoreCase(email)) {
			throw new EmailAlreadyRegisteredException();
		}
		AppUser user = new AppUser(email, passwordEncoder.encode(request.password()), clock.instant());
		try {
			users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			// Two registrations with the same email at the same moment: the unique index
			// stops the second.
			throw new EmailAlreadyRegisteredException();
		}
		log.info("User registered: id={}", user.getId());
		return UserResponse.from(user);
	}

	/**
	 * The error is the same for an unknown email and a wrong password, so attackers can't
	 * use the login form to find out which emails have accounts.
	 */
	public TokenResponse login(LoginRequest request, String clientIp) {
		String email = normalise(request.email());
		try {
			limiter.checkAllowed(clientIp, email);
		}
		catch (TooManyRequestsException ex) {
			countLogin("rate_limited");
			log.warn("Login rate limit reached");
			throw ex;
		}
		AppUser user = users.findByEmailIgnoreCase(email).orElse(null);
		String hash = (user != null) ? user.getPasswordHash() : dummyHash;
		boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
		if (user == null || !passwordMatches) {
			limiter.recordFailure(email);
			countLogin("failure");
			log.info("Failed login attempt");
			throw new BadCredentialsException("Invalid email or password");
		}
		limiter.recordSuccess(email);
		countLogin("success");
		IssuedToken token = tokenService.issue(user.getId());
		log.info("User logged in: id={}", user.getId());
		return new TokenResponse(token.value(), "Bearer", token.lifetime().toSeconds());
	}

	/** A rising failure or rate_limited count is a sign of password-guessing attacks. */
	private void countLogin(String result) {
		meters.counter("salon.auth.logins", "result", result).increment();
	}

	private static String normalise(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
