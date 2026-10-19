package io.github.afunemma.salonbooking.salon;

import org.jspecify.annotations.Nullable;

/**
 * How prices are shown to people. Prices are stored as cents (ADR-0003); this turns them
 * into rand, the same way on every page.
 */
public final class Prices {

	private Prices() {
	}

	/** R50 for 5000 cents, R75.50 for 7550 cents. */
	public static String rand(int cents) {
		return cents % 100 == 0 ? "R" + cents / 100 : "R%d.%02d".formatted(cents / 100, cents % 100);
	}

	/**
	 * "R50–R100" for a range, "R200" for one price, or {@code null} when there is no
	 * price.
	 */
	public static @Nullable String range(@Nullable Integer fromCents, @Nullable Integer toCents) {
		if (fromCents == null) {
			return null;
		}
		if (toCents == null || toCents.equals(fromCents)) {
			return rand(fromCents);
		}
		return rand(fromCents) + "–" + rand(toCents);
	}

}
