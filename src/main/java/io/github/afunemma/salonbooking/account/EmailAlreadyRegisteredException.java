package io.github.afunemma.salonbooking.account;

import io.github.afunemma.salonbooking.common.ConflictException;

public class EmailAlreadyRegisteredException extends ConflictException {

	public EmailAlreadyRegisteredException() {
		super("Email already registered", "An account with this email already exists");
	}

}
