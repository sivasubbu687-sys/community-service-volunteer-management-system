package com.community.volunteer_management.security;

import com.community.volunteer_management.entity.Role;

// The logged-in user, stored as the principal of the current request
public record AuthUser(Long id, String name, String email, Role role, Long volunteerId) {
}
