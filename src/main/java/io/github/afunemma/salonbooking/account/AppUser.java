package io.github.afunemma.salonbooking.account;

import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A salon owner's account. Named {@code AppUser} because {@code user} is a reserved word
 * in SQL.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private @Nullable Long id;

	@Column(nullable = false)
	private String email;

	/** Never the password itself: a one-way BCrypt hash, e.g. "{bcrypt}$2a$10$...". */
	@Column(nullable = false)
	private String passwordHash;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	protected AppUser() {
	}

	public AppUser(String email, String passwordHash, Instant createdAt) {
		this.email = Objects.requireNonNull(email, "email must not be null");
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash must not be null");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
	}

	/** Only available once saved; the database assigns the id. */
	public Long getId() {
		return Objects.requireNonNull(id, "not saved yet");
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
