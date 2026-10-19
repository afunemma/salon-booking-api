package io.github.afunemma.salonbooking.owner;

/**
 * The logged-in owner, kept in their session. Holds only the user id and email, never the
 * password.
 */
public record OwnerPrincipal(Long userId, String email) {
}
