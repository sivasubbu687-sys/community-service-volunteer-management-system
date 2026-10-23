package com.community.volunteer_management.dto;

import java.time.LocalDateTime;

// Used by ADMIN / COORDINATOR to correct check-in and/or check-out times (either field may be omitted)
public record AttendanceRequest(LocalDateTime checkInTime, LocalDateTime checkOutTime) {
}
