package com.community.volunteer_management.dto;

import com.community.volunteer_management.entity.Assignment;
import com.community.volunteer_management.entity.AssignmentStatus;
import com.community.volunteer_management.entity.AttendanceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// Flat view of an assignment (no nested entities, so no infinite recursion in JSON)
public record AssignmentResponse(
		Long id,
		Long volunteerId,
		String volunteerName,
		String volunteerEmail,
		String volunteerPhone,
		Long eventId,
		String eventTitle,
		LocalDate eventDate,
		LocalTime startTime,
		LocalTime endTime,
		String location,
		AssignmentStatus status,
		LocalDateTime assignedAt,
		LocalDateTime checkInTime,
		LocalDateTime checkOutTime,
		double hoursWorked,
		AttendanceStatus attendanceStatus,
		String emailStatus) {   // only filled in when an assignment is created

	public static AssignmentResponse from(Assignment a) {
		return new AssignmentResponse(
				a.getId(),
				a.getVolunteer().getId(),
				a.getVolunteer().getName(),
				a.getVolunteer().getEmail(),
				a.getVolunteer().getPhone(),
				a.getEvent().getId(),
				a.getEvent().getTitle(),
				a.getEvent().getEventDate(),
				a.getEvent().getStartTime(),
				a.getEvent().getEndTime(),
				a.getEvent().getLocation(),
				a.getStatus(),
				a.getAssignedAt(),
				a.getCheckInTime(),
				a.getCheckOutTime(),
				a.getHoursWorked(),
				a.getAttendanceStatus(),
				null);
	}

	public AssignmentResponse withEmailStatus(String emailStatus) {
		return new AssignmentResponse(id, volunteerId, volunteerName, volunteerEmail, volunteerPhone, eventId,
				eventTitle, eventDate, startTime, endTime, location, status, assignedAt, checkInTime, checkOutTime,
				hoursWorked, attendanceStatus, emailStatus);
	}
}
