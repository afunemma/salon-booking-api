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

@Service
@Transactional(readOnly = true)
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokenService;

	private final Clock clock;

	/**
	 * Checked against when the email doesn't exist, so a failed login takes the same time
	 * whether or not the account exists. Otherwise response times would reveal which
	 * emails are registered.
	 */
	private final String dummyHash;

	AuthService(AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.clock = clock;
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
	public TokenResponse login(LoginRequest request) {
		AppUser user = users.findByEmailIgnoreCase(normalise(request.email())).orElse(null);
		String hash = (user != null) ? user.getPasswordHash() : dummyHash;
		boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
		if (user == null || !passwordMatches) {
			log.info("Failed login attempt");
			throw new BadCredentialsException("Invalid email or password");
		}
		IssuedToken token = tokenService.issue(user.getId());
		log.info("User logged in: id={}", user.getId());
		return new TokenResponse(token.value(), "Bearer", token.lifetime().toSeconds());
	}

	private static String normalise(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
