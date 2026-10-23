package com.community.volunteer_management.dto;

// How many volunteers are actively assigned to an event (safe to show to every logged-in user)
public record EventSpotsResponse(Long eventId, long assigned) {
}
