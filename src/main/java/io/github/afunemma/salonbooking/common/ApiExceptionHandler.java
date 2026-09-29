package io.github.afunemma.salonbooking.common;

import java.util.Map;
import java.util.TreeMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import io.github.afunemma.salonbooking.booking.SlotUnavailableException;

/**
 * Turns exceptions into consistent JSON error responses using the
 * RFC 9457 "Problem Details" format, e.g.
 * <pre>{"status": 409, "title": "Slot unavailable", "detail": "10:00 is not free on 2030-01-07"}</pre>
 * Extending {@link ResponseEntityExceptionHandler} gives the same format for Spring's
 * own errors, such as failed {@code @Valid} checks and malformed JSON.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	ProblemDetail handleNotFound(NotFoundException ex) {
		return problem(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
	}

	@ExceptionHandler(SlotUnavailableException.class)
	ProblemDetail handleSlotUnavailable(SlotUnavailableException ex) {
		return problem(HttpStatus.CONFLICT, "Slot unavailable", ex.getMessage());
	}

	/** Invalid state change, e.g. cancelling a booking that is already completed. */
	@ExceptionHandler(IllegalStateException.class)
	ProblemDetail handleIllegalState(IllegalStateException ex) {
		return problem(HttpStatus.CONFLICT, "Conflict", ex.getMessage());
	}

	/** Business rule broken by the input, e.g. a salon that closes before it opens. */
	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
		return problem(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
	}

	/** Lists every invalid field, e.g. {"errors": {"clientPhone": "must not be blank"}}. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		Map<String, String> errors = new TreeMap<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(error.getField(), error.getDefaultMessage());
		}
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "One or more fields are invalid");
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(problem);
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		return problem;
	}
}
