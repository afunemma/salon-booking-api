package io.github.afunemma.salonbooking.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.JwsAlgorithms;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

/**
 * Issues login tokens and reads the user id back out of them.
 */
@Service
public class TokenService {

	private final JwtEncoder encoder;

	private final Clock clock;

	private final Duration lifetime;

	TokenService(JwtEncoder encoder, Clock clock, AppProperties properties) {
		this.encoder = encoder;
		this.clock = clock;
		this.lifetime = properties.security().tokenLifetime();
	}

	/**
	 * Creates a signed token for a user. It holds only the user's id (as the subject),
	 * not their email, because anyone holding a token can read its contents.
	 */
	public IssuedToken issue(Long userId) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(JwtConfig.ISSUER)
			.subject(userId.toString())
			.issuedAt(now)
			.expiresAt(now.plus(lifetime))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.from(JwsAlgorithms.HS256)).build();
		String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new IssuedToken(value, lifetime);
	}

	/**
	 * The id of the logged-in user, from a token that Spring Security has already
	 * verified.
	 */
	public static Long userIdOf(Jwt token) {
		return Long.valueOf(token.getSubject());
	}

	public record IssuedToken(String value, Duration lifetime) {
	}

}
