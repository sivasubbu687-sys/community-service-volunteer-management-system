package com.community.volunteer_management.dto;

import jakarta.validation.constraints.NotNull;

public record AssignmentRequest(
		@NotNull(message = "volunteerId is required") Long volunteerId,
		@NotNull(message = "eventId is required") Long eventId) {
}
