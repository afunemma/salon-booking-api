package io.github.afunemma.salonbooking.common;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/**
 * Swagger UI setup. Endpoints marked {@code @SecurityRequirement(name = BEARER_AUTH)}
 * show a lock icon; the Authorize button takes the token from
 * {@code POST /api/v1/auth/login}.
 */
@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(title = "Salon Booking API", version = "v1",
		description = "Clients find free times and book without an account. Salon owners log in to manage their salons."))
@SecurityScheme(name = OpenApiConfig.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer",
		bearerFormat = "JWT")
public class OpenApiConfig {

	public static final String BEARER_AUTH = "bearerAuth";

}
