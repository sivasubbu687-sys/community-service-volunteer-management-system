package com.community.volunteer_management.dto;

import com.community.volunteer_management.entity.Role;
import com.community.volunteer_management.entity.User;

// A user without the password hash
public record UserResponse(
		Long id,
		String name,
		String email,
		Role role,
		Long volunteerId) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(),
				user.getVolunteer() != null ? user.getVolunteer().getId() : null);
	}
}
