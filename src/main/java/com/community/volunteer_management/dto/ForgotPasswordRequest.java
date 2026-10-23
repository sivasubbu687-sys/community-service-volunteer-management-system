package com.community.volunteer_management.dto;

import jakarta.validation.constraints.NotBlank;

// Only "not blank" is checked on purpose: every other value gets the same generic answer,
// so the response never reveals whether an email is registered.
public record ForgotPasswordRequest(
		@NotBlank(message = "Email is required") String email) {
}
