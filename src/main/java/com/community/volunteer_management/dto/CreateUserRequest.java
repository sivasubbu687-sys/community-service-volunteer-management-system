package com.community.volunteer_management.dto;

import com.community.volunteer_management.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Used by an ADMIN to create accounts. volunteerId links a VOLUNTEER account to an existing volunteer record.
public record CreateUserRequest(
		@NotBlank(message = "Name is required") String name,
		@NotBlank(message = "Email is required") @Email(message = "Email address is not valid") String email,
		@NotBlank(message = "Password is required") @Size(min = 6, message = "Password must be at least 6 characters") String password,
		@NotNull(message = "Role is required") Role role,
		Long volunteerId) {
}
