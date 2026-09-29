package io.github.afunemma.salonbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SlotFinderTest {

	private static final OpeningHours NINE_TO_EIGHT = new OpeningHours(LocalTime.of(9, 0), LocalTime.of(20, 0));
	private static final ServiceOffering HAIRCUT = new ServiceOffering("Haircut", Duration.ofMinutes(35));
	private static final Duration EVERY_15_MIN = Duration.ofMinutes(15);

	@Test
	@DisplayName("With no bookings, the day starts at opening and the last slot still ends before closing")
	void emptyDay() {
		List<LocalTime> slots = SlotFinder.findFreeSlots(NINE_TO_EIGHT, List.of(), HAIRCUT, EVERY_15_MIN);

		assertThat(slots).first().isEqualTo(LocalTime.of(9, 0));
		// 19:15 + 35 min = 19:50 fits; 19:30 + 35 min = 20:05 would run past closing.
		assertThat(slots).last().isEqualTo(LocalTime.of(19, 15));
	}

	@Test
	@DisplayName("Times that would overlap an existing booking are not offered")
	void hidesClashingTimes() {
		Booking thabo = new Booking("Thabo", LocalTime.of(10, 0), HAIRCUT.duration());

		List<LocalTime> slots = SlotFinder.findFreeSlots(NINE_TO_EIGHT, List.of(thabo), HAIRCUT, EVERY_15_MIN);

		assertThat(slots)
				.contains(LocalTime.of(9, 15), LocalTime.of(10, 45))
				.doesNotContain(LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(10, 30));
	}

	@Test
	@DisplayName("A new booking may start exactly when the previous one ends")
	void backToBackBookingsAreAllowed() {
		Booking thabo = new Booking("Thabo", LocalTime.of(10, 0), HAIRCUT.duration());

		List<LocalTime> slots = SlotFinder.findFreeSlots(NINE_TO_EIGHT, List.of(thabo), HAIRCUT, Duration.ofMinutes(5));

		assertThat(slots).contains(LocalTime.of(10, 35));
	}

	@Test
	@DisplayName("A long service only fits where there is a big enough gap")
	void longServiceNeedsBigGap() {
		ServiceOffering braids = new ServiceOffering("Box braids", Duration.ofHours(5));
		Booking lunch = new Booking("Lunch", LocalTime.of(13, 0), Duration.ofHours(1));

		List<LocalTime> slots = SlotFinder.findFreeSlots(NINE_TO_EIGHT, List.of(lunch), braids, Duration.ofHours(1));

		// Before lunch there are only 4 hours, so braids can start only from 14:00 onwards.
		assertThat(slots).containsExactly(LocalTime.of(14, 0), LocalTime.of(15, 0));
	}

	@Test
	@DisplayName("A service longer than the whole day has no slots")
	void serviceLongerThanDay() {
		OpeningHours shortDay = new OpeningHours(LocalTime.of(9, 0), LocalTime.of(10, 0));
		ServiceOffering twoHours = new ServiceOffering("Relaxer", Duration.ofHours(2));

		assertThat(SlotFinder.findFreeSlots(shortDay, List.of(), twoHours, EVERY_15_MIN)).isEmpty();
	}

	@Test
	@DisplayName("Late-night opening hours do not wrap around midnight")
	void doesNotWrapPastMidnight() {
		OpeningHours lateDay = new OpeningHours(LocalTime.of(22, 0), LocalTime.of(23, 59));
		ServiceOffering oneHour = new ServiceOffering("Colour", Duration.ofHours(1));

		List<LocalTime> slots = SlotFinder.findFreeSlots(lateDay, List.of(), oneHour, Duration.ofMinutes(30));

		assertThat(slots).containsExactly(LocalTime.of(22, 0), LocalTime.of(22, 30));
	}

	@Test
	@DisplayName("Step must be at least one minute, otherwise the search would never move forward")
	void rejectsStepShorterThanAMinute() {
		assertThatThrownBy(() -> SlotFinder.findFreeSlots(NINE_TO_EIGHT, List.of(), HAIRCUT, Duration.ZERO))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> SlotFinder.findFreeSlots(NINE_TO_EIGHT, List.of(), HAIRCUT, Duration.ofSeconds(30)))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
