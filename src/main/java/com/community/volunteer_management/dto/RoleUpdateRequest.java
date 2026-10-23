package com.community.volunteer_management.dto;

import com.community.volunteer_management.entity.Role;
import jakarta.validation.constraints.NotNull;

public record RoleUpdateRequest(
		@NotNull(message = "role is required") Role role) {
}
