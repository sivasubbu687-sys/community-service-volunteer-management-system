package com.community.volunteer_management.controller;

import com.community.volunteer_management.dto.AssignmentRequest;
import com.community.volunteer_management.dto.AssignmentResponse;
import com.community.volunteer_management.dto.AssignmentStatusRequest;
import com.community.volunteer_management.dto.EventSpotsResponse;
import com.community.volunteer_management.security.AuthUser;
import com.community.volunteer_management.dto.AttendanceRequest;
import com.community.volunteer_management.dto.TopVolunteerResponse;
import com.community.volunteer_management.dto.VolunteerHoursResponse;
import com.community.volunteer_management.service.AssignmentService;
import com.community.volunteer_management.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AssignmentController {

	private final AssignmentService assignmentService;
	private final AttendanceService attendanceService;

	public AssignmentController(AssignmentService assignmentService, AttendanceService attendanceService) {
		this.assignmentService = assignmentService;
		this.attendanceService = attendanceService;
	}

	@GetMapping("/assignments")
	public List<AssignmentResponse> getAllAssignments() {
		return assignmentService.findAll();
	}

	@PostMapping("/assignments")
	@ResponseStatus(HttpStatus.CREATED)
	public AssignmentResponse createAssignment(@Valid @RequestBody AssignmentRequest request) {
		return assignmentService.create(request);
	}

	@PutMapping("/assignments/{id}/status")
	public AssignmentResponse updateAssignmentStatus(@PathVariable Long id,
			@Valid @RequestBody AssignmentStatusRequest request) {
		return assignmentService.updateStatus(id, request.status());
	}

	@DeleteMapping("/assignments/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteAssignment(@PathVariable Long id) {
		assignmentService.delete(id);
	}

	// ---------- Attendance & hours ----------

	@PutMapping("/assignments/{id}/check-in")
	public AssignmentResponse checkIn(@PathVariable Long id) {
		return attendanceService.checkIn(id);
	}

	@PutMapping("/assignments/{id}/check-out")
	public AssignmentResponse checkOut(@PathVariable Long id) {
		return attendanceService.checkOut(id);
	}

	// ADMIN / COORDINATOR correct a forgotten or wrong check-in / check-out time
	@PutMapping("/assignments/{id}/attendance")
	public AssignmentResponse correctAttendance(@PathVariable Long id, @RequestBody AttendanceRequest request) {
		return attendanceService.correctTimes(id, request);
	}

	@PutMapping("/assignments/{id}/absent")
	public AssignmentResponse markAbsent(@PathVariable Long id) {
		return attendanceService.markAbsent(id);
	}

	@GetMapping("/events/{eventId}/attendance")
	public List<AssignmentResponse> getEventAttendance(@PathVariable Long eventId) {
		return attendanceService.findAttendanceByEvent(eventId);
	}

	@GetMapping("/volunteers/{volunteerId}/hours")
	public VolunteerHoursResponse getVolunteerHours(@PathVariable Long volunteerId) {
		return attendanceService.findHours(volunteerId);
	}

	@GetMapping("/reports/top-volunteers")
	public List<TopVolunteerResponse> getTopVolunteers() {
		return attendanceService.findTopVolunteers(5);
	}

	// ---------- Volunteer self sign-up ----------

	@PostMapping("/events/{eventId}/join")
	@ResponseStatus(HttpStatus.CREATED)
	public AssignmentResponse joinEvent(@PathVariable Long eventId, @AuthenticationPrincipal AuthUser user) {
		return assignmentService.join(eventId, requireVolunteerId(user));
	}

	@PostMapping("/events/{eventId}/leave")
	public AssignmentResponse leaveEvent(@PathVariable Long eventId, @RequestBody java.util.Map<String, String> body,
			@AuthenticationPrincipal AuthUser user) {
		return assignmentService.leave(eventId, requireVolunteerId(user), body.get("reason"));
	}

	// How many volunteers are assigned to each event (any logged-in user)
	@GetMapping("/events/summary")
	public List<EventSpotsResponse> getEventSpots() {
		return assignmentService.findSpots();
	}

	private Long requireVolunteerId(AuthUser user) {
		if (user.volunteerId() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your account has no volunteer profile");
		}
		return user.volunteerId();
	}

	// Assignments (with volunteer details) for one event
	@GetMapping("/events/{eventId}/volunteers")
	public List<AssignmentResponse> getVolunteersForEvent(@PathVariable Long eventId) {
		return assignmentService.findByEvent(eventId);
	}

	// Assignments (with event details) for one volunteer
	@GetMapping("/volunteers/{volunteerId}/events")
	public List<AssignmentResponse> getEventsForVolunteer(@PathVariable Long volunteerId) {
		return assignmentService.findByVolunteer(volunteerId);
	}
}
