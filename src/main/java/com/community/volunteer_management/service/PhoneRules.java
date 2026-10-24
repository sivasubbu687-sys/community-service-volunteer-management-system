package com.community.volunteer_management.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// Indian mobile numbers: exactly 10 digits, starting with 6, 7, 8 or 9
final class PhoneRules {

	private PhoneRules() {
	}

	static String check(String phone) {
		String value = phone == null ? "" : phone.trim();
		if (!value.matches("[6-9][0-9]{9}")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Phone number must be 10 digits and start with 6, 7, 8 or 9");
		}
		return value;
	}
}
