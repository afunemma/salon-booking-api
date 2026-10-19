package io.github.afunemma.salonbooking.owner;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.github.afunemma.salonbooking.common.NotFoundException;

/**
 * Errors on the owner pages become a page, not the API's JSON. Checked before the API's
 * handler, and only for controllers in this package.
 * <p>
 * Another owner's salon looks the same as one that doesn't exist, so the pages can't be
 * used to discover other salons.
 */
@ControllerAdvice(basePackageClasses = OwnerPagesExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class OwnerPagesExceptionHandler {

	@ExceptionHandler({ NotFoundException.class, AccessDeniedException.class })
	@ResponseStatus(HttpStatus.NOT_FOUND)
	String notFound() {
		return "owner/not-found";
	}

}
