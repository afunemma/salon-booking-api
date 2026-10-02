package io.github.afunemma.salonbooking.common;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Creates and checks login tokens (JWTs) signed with HMAC-SHA256.
 * <p>
 * Uses Spring Security's own JWT support (Nimbus) rather than hand-written token code.
 */
@Configuration(proxyBeanMethods = false)
class JwtConfig {

	/** The {@code iss} claim: identifies tokens issued by this application. */
	static final String ISSUER = "salon-booking-api";

	private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

	@Bean
	SecretKey jwtSigningKey(AppProperties properties) {
		String secret = properties.security().jwtSecret();
		if (secret != null) {
			return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
		}
		log.warn("No app.security.jwt-secret configured: using a random key. "
				+ "Login tokens will stop working when the app restarts. Set APP_SECURITY_JWT_SECRET in production.");
		byte[] randomKey = new byte[32];
		new SecureRandom().nextBytes(randomKey);
		return new SecretKeySpec(randomKey, "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSigningKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();
		// Checks the signature, expiry and that the token was issued by this app.
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
		return decoder;
	}

}
