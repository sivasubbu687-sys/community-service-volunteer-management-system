package com.community.volunteer_management.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank(message = "Name is required") String name,
		@NotBlank(message = "Email is required") @Email(message = "Email address is not valid") String email,
		@NotBlank(message = "Phone is required") String phone,
		@NotBlank(message = "Password is required") @Size(min = 6, message = "Password must be at least 6 characters") String password) {
}
