package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TimeRangeTest {

	private static final TimeRange TEN_TO_TEN_35 = new TimeRange(LocalTime.of(10, 0), LocalTime.of(10, 35));

	@Test
	@DisplayName("Ranges that share any time overlap")
	void overlapping() {
		assertThat(TEN_TO_TEN_35.overlaps(range(10, 30, 11, 0))).isTrue();
		assertThat(TEN_TO_TEN_35.overlaps(range(9, 45, 10, 5))).isTrue();
		// One range fully inside the other
		assertThat(TEN_TO_TEN_35.overlaps(range(10, 10, 10, 20))).isTrue();
	}

	@Test
	@DisplayName("Back-to-back ranges do not overlap")
	void touching() {
		assertThat(TEN_TO_TEN_35.overlaps(range(10, 35, 11, 10))).isFalse();
		assertThat(TEN_TO_TEN_35.overlaps(range(9, 25, 10, 0))).isFalse();
	}

	@Test
	@DisplayName("Start must be before end")
	void rejectsEmptyOrBackwardsRange() {
		assertThatThrownBy(() -> range(10, 0, 10, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> range(11, 0, 10, 0)).isInstanceOf(IllegalArgumentException.class);
	}

	private static TimeRange range(int startHour, int startMinute, int endHour, int endMinute) {
		return new TimeRange(LocalTime.of(startHour, startMinute), LocalTime.of(endHour, endMinute));
	}
}
