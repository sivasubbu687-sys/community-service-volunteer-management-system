package com.community.volunteer_management.dto;

import com.community.volunteer_management.entity.Role;

public record AuthResponse(
		String token,
		Long userId,
		String name,
		String email,
		Role role,
		Long volunteerId) {
}
