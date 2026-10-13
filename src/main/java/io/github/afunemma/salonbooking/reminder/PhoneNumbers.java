package io.github.afunemma.salonbooking.reminder;

/**
 * Converts the phone numbers clients type into the international form WhatsApp expects:
 * digits only, starting with the country code.
 */
final class PhoneNumbers {

	private PhoneNumbers() {
	}

	/**
	 * Examples with country code 27 (South Africa): {@code 082 123 4567} →
	 * {@code 27821234567}; {@code +27 82 123 4567} → {@code 27821234567}.
	 * @throws IllegalArgumentException if the number has no digits after cleaning
	 */
	static String toInternational(String phone, String defaultCountryCode) {
		String digits = phone.replaceAll("[^0-9]", "");
		if (digits.isEmpty()) {
			throw new IllegalArgumentException("Phone number has no digits");
		}
		if (phone.strip().startsWith("+")) {
			return digits;
		}
		if (digits.startsWith("00")) {
			return digits.substring(2);
		}
		if (digits.startsWith("0")) {
			return defaultCountryCode + digits.substring(1);
		}
		return digits;
	}

}
