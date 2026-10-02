package io.github.afunemma.salonbooking.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request and response bodies for registration and login.
 */
public final class AuthDtos {

	private AuthDtos() {
	}

	/**
	 * Passwords must be 12 to 72 characters. Length matters more than symbols for
	 * strength, and BCrypt ignores everything after 72 bytes, so longer passwords would
	 * be misleading.
	 */
	public record RegisterRequest(@NotBlank @Email @Size(max = 320) String email,
			@NotBlank @Size(min = 12, max = 72) String password) {

		@Override
		public String toString() {
			return "RegisterRequest[email=" + email + ", password=****]";
		}
	}

	public record LoginRequest(@NotBlank String email, @NotBlank String password) {

		@Override
		public String toString() {
			return "LoginRequest[email=" + email + ", password=****]";
		}
	}

	public record UserResponse(Long id, String email) {

		static UserResponse from(AppUser user) {
			return new UserResponse(user.getId(), user.getEmail());
		}
	}

	/**
	 * Follows the OAuth 2 token response shape: {"accessToken": "...", "tokenType":
	 * "Bearer", "expiresIn": 3600}.
	 */
	public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
	}

}
