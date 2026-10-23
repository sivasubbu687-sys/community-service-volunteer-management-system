package com.community.volunteer_management.dto;

import com.community.volunteer_management.entity.AttendanceStatus;

import java.time.LocalDate;
import java.util.List;

public record VolunteerHoursResponse(
		Long volunteerId,
		String volunteerName,
		double totalHours,
		List<EventHours> events) {

	public record EventHours(
			Long assignmentId,
			Long eventId,
			String eventTitle,
			LocalDate eventDate,
			AttendanceStatus attendanceStatus,
			double hoursWorked) {
	}
}
