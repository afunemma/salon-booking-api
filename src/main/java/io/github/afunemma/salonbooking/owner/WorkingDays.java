package io.github.afunemma.salonbooking.owner;

import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Describes working days briefly: "Every day", "Tue–Sat", or "Mon, Wed, Fri".
 */
final class WorkingDays {

	private WorkingDays() {
	}

	static String describe(List<DayOfWeek> days) {
		List<DayOfWeek> sorted = days.stream().sorted().toList();
		if (sorted.size() == 7) {
			return "Every day";
		}
		boolean oneRun = sorted.size() > 2
				&& sorted.getLast().getValue() - sorted.getFirst().getValue() == sorted.size() - 1;
		if (oneRun) {
			return shortName(sorted.getFirst()) + "–" + shortName(sorted.getLast());
		}
		List<String> names = new ArrayList<>();
		sorted.forEach(day -> names.add(shortName(day)));
		return String.join(", ", names);
	}

	static String shortName(DayOfWeek day) {
		return day.getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
	}

}
