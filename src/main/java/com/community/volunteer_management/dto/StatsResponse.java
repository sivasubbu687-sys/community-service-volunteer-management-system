package com.community.volunteer_management.dto;

// Community-wide totals shown on the volunteer home page (no personal details)
public record StatsResponse(
		long activeVolunteers,
		long communityEvents,
		double hoursContributed,
		long shiftsCompleted) {
}
