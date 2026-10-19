package io.github.afunemma.salonbooking.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class ApiExceptionHandlerTest {

	@Test
	@DisplayName("A change refused by optimistic locking becomes 409, telling the client to reload and retry")
	void concurrentChangeIs409() {
		ProblemDetail problem = new ApiExceptionHandler()
			.handleConcurrentChange(new ObjectOptimisticLockingFailureException("Booking", 1L));

		assertThat(problem.getStatus()).isEqualTo(409);
		assertThat(problem.getTitle()).isEqualTo("Changed at the same time");
		assertThat(problem.getDetail()).contains("Reload and try again");
	}

}
