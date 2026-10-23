package com.community.volunteer_management.dto;

// Which volunteers have a photo, and its version (no picture data)
public record PhotoInfoResponse(Long volunteerId, long version) {
}
