package com.community.volunteer_management.controller;

import com.community.volunteer_management.dto.StatsResponse;
import com.community.volunteer_management.repository.AssignmentRepository;
import com.community.volunteer_management.repository.EventRepository;
import com.community.volunteer_management.repository.VolunteerRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Any logged-in user may read these totals (see SecurityConfig: everything else is "authenticated" by default)
@RestController
@RequestMapping("/api/stats")
public class StatsController {

	private final VolunteerRepository volunteerRepository;
	private final EventRepository eventRepository;
	private final AssignmentRepository assignmentRepository;

	public StatsController(VolunteerRepository volunteerRepository, EventRepository eventRepository,
			AssignmentRepository assignmentRepository) {
		this.volunteerRepository = volunteerRepository;
		this.eventRepository = eventRepository;
		this.assignmentRepository = assignmentRepository;
	}

	@GetMapping("/overview")
	public StatsResponse overview() {
		double hours = Math.round(assignmentRepository.totalHoursWorked() * 100.0) / 100.0;
		return new StatsResponse(volunteerRepository.count(), eventRepository.count(), hours,
				assignmentRepository.countCompletedShifts());
	}
}
