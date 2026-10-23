package com.community.volunteer_management.dto;

public record TopVolunteerResponse(
		Long volunteerId,
		String name,
		String email,
		double totalHours,
		long eventsWorked) {
}
