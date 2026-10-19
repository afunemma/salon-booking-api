package io.github.afunemma.salonbooking.owner;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import io.github.afunemma.salonbooking.account.AuthService;
import io.github.afunemma.salonbooking.owner.OwnerAuthenticationProvider.LoginRateLimitedException;

/**
 * Security for the owner pages under {@code /owner}, separate from the API's (see
 * ADR-0019).
 * <p>
 * A browser page can't send the API's {@code Authorization: Bearer} header without
 * JavaScript keeping the token where scripts can read it. So owner pages use a normal
 * login form and a session cookie that scripts can't read (HttpOnly), and therefore
 * <b>CSRF protection is on</b>: every form carries a secret token, so another website
 * can't make an owner's browser cancel bookings. The API chain stays stateless, with CSRF
 * off, because it uses no cookies.
 */
@Configuration(proxyBeanMethods = false)
class OwnerSecurityConfig {

	/** Checked before the API's chain, and only for {@code /owner} pages. */
	@Bean
	@Order(1)
	SecurityFilterChain ownerPages(HttpSecurity http, AuthService authService) throws Exception {
		http.securityMatcher("/owner/**")
			.authorizeHttpRequests(requests -> requests.requestMatchers("/owner/login", "/owner/register")
				.permitAll()
				.anyRequest()
				.hasRole("OWNER"))
			.authenticationManager(new ProviderManager(new OwnerAuthenticationProvider(authService)))
			.formLogin(form -> form.loginPage("/owner/login")
				.loginProcessingUrl("/owner/login")
				.usernameParameter("email")
				.defaultSuccessUrl("/owner", true)
				.failureHandler((request, response,
						ex) -> response.sendRedirect(request.getContextPath() + (ex instanceof LoginRateLimitedException
								? "/owner/login?error=too-many" : "/owner/login?error"))))
			.logout(logout -> logout.logoutUrl("/owner/logout").logoutSuccessUrl("/owner/login?logout"));
		return http.build();
	}

}
