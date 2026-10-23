package com.community.volunteer_management.dto;

// Which events have a picture, and its version (no picture data)
public record EventPhotoInfoResponse(Long eventId, long version) {
}
