package com.community.volunteer_management.dto;

import com.community.volunteer_management.entity.AssignmentStatus;
import jakarta.validation.constraints.NotNull;

public record AssignmentStatusRequest(
		@NotNull(message = "status is required") AssignmentStatus status) {
}
