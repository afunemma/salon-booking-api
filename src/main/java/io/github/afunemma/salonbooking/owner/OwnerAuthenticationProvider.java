package io.github.afunemma.salonbooking.owner;

import java.util.List;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import io.github.afunemma.salonbooking.account.AuthService;
import io.github.afunemma.salonbooking.common.TooManyRequestsException;

/**
 * Checks the owner login form through the same {@link AuthService#authenticate} as the
 * API, so the form gets the same rate limits and the same "wrong email or password"
 * answer for both mistakes.
 */
class OwnerAuthenticationProvider implements AuthenticationProvider {

	private final AuthService authService;

	OwnerAuthenticationProvider(AuthService authService) {
		this.authService = authService;
	}

	@Override
	public Authentication authenticate(Authentication authentication) {
		String email = authentication.getName();
		String password = String.valueOf(authentication.getCredentials());
		String clientIp = authentication.getDetails() instanceof WebAuthenticationDetails details
				&& details.getRemoteAddress() != null ? details.getRemoteAddress() : "unknown";
		try {
			Long userId = authService.authenticate(email, password, clientIp);
			// The password is not kept: the session only holds who logged in.
			return UsernamePasswordAuthenticationToken.authenticated(new OwnerPrincipal(userId, email), null,
					List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
		}
		catch (TooManyRequestsException ex) {
			throw new LoginRateLimitedException(ex.getMessage());
		}
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}

	/** Lets the login page say "too many attempts" instead of "wrong password". */
	static class LoginRateLimitedException extends AuthenticationException {

		LoginRateLimitedException(String message) {
			super(message);
		}

	}

}
