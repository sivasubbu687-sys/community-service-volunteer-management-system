package com.community.volunteer_management.service;

import com.community.volunteer_management.dto.AssignmentResponse;
import com.community.volunteer_management.dto.AttendanceRequest;
import com.community.volunteer_management.dto.TopVolunteerResponse;
import com.community.volunteer_management.dto.VolunteerHoursResponse;
import com.community.volunteer_management.entity.Assignment;
import com.community.volunteer_management.entity.AssignmentStatus;
import com.community.volunteer_management.entity.AttendanceStatus;
import com.community.volunteer_management.entity.Event;
import com.community.volunteer_management.entity.EventStatus;
import com.community.volunteer_management.entity.Volunteer;
import com.community.volunteer_management.repository.AssignmentRepository;
import com.community.volunteer_management.repository.EventRepository;
import com.community.volunteer_management.repository.VolunteerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class AttendanceService {

	// Volunteers may check in this many minutes before the event starts
	private static final long EARLY_CHECK_IN_MINUTES = 30;

	private final AssignmentRepository assignmentRepository;
	private final VolunteerRepository volunteerRepository;
	private final EventRepository eventRepository;

	public AttendanceService(AssignmentRepository assignmentRepository, VolunteerRepository volunteerRepository,
			EventRepository eventRepository) {
		this.assignmentRepository = assignmentRepository;
		this.volunteerRepository = volunteerRepository;
		this.eventRepository = eventRepository;
	}

	// Check-in and check-out use the server's current time. They are allowed from 30 minutes before the
	// event starts until the event ends. Forgotten or wrong times are fixed with correctTimes().
	public AssignmentResponse checkIn(Long id) {
		Assignment assignment = findById(id);
		checkAttendanceAllowed(assignment);
		LocalDateTime now = LocalDateTime.now();
		checkWithinEventWindow(assignment.getEvent(), now, "Check-in");
		if (assignment.getCheckInTime() != null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					assignment.getVolunteer().getName() + " has already checked in at " + assignment.getCheckInTime());
		}
		assignment.setCheckInTime(now);
		assignment.setAttendanceStatus(AttendanceStatus.PRESENT);
		return AssignmentResponse.from(assignmentRepository.save(assignment));
	}

	public AssignmentResponse checkOut(Long id) {
		Assignment assignment = findById(id);
		checkAttendanceAllowed(assignment);
		LocalDateTime now = LocalDateTime.now();
		checkWithinEventWindow(assignment.getEvent(), now, "Check-out");
		if (assignment.getCheckInTime() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot check out before checking in");
		}
		if (assignment.getCheckOutTime() != null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					assignment.getVolunteer().getName() + " has already checked out at " + assignment.getCheckOutTime());
		}
		if (!now.isAfter(assignment.getCheckInTime())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Check-out time must be after check-in time (" + assignment.getCheckInTime() + ")");
		}
		assignment.setCheckOutTime(now);
		assignment.setHoursWorked(hoursBetween(assignment.getCheckInTime(), now));
		return AssignmentResponse.from(assignmentRepository.save(assignment));
	}

	// ADMIN / COORDINATOR fix a forgotten or wrong check-in / check-out time (not limited to the event window)
	public AssignmentResponse correctTimes(Long id, AttendanceRequest request) {
		Assignment assignment = findById(id);
		checkAttendanceAllowed(assignment);
		if (request == null || (request.checkInTime() == null && request.checkOutTime() == null)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide checkInTime and/or checkOutTime");
		}
		LocalDateTime in = request.checkInTime() != null ? request.checkInTime() : assignment.getCheckInTime();
		LocalDateTime out = request.checkOutTime() != null ? request.checkOutTime() : assignment.getCheckOutTime();
		if (in == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"A check-in time is required before a check-out time can be set");
		}
		LocalDateTime now = LocalDateTime.now();
		if (in.isAfter(now) || (out != null && out.isAfter(now))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Times cannot be in the future");
		}
		if (out != null) {
			if (!out.isAfter(in)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Check-out time must be after check-in time");
			}
			if (Duration.between(in, out).toHours() >= 24) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A single shift cannot be 24 hours or longer");
			}
			assignment.setCheckOutTime(out);
			assignment.setHoursWorked(hoursBetween(in, out));
		}
		assignment.setCheckInTime(in);
		assignment.setAttendanceStatus(AttendanceStatus.PRESENT);
		return AssignmentResponse.from(assignmentRepository.save(assignment));
	}

	public AssignmentResponse markAbsent(Long id) {
		Assignment assignment = findById(id);
		checkAttendanceAllowed(assignment);
		if (assignment.getCheckInTime() != null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					assignment.getVolunteer().getName() + " has already checked in, so cannot be marked absent");
		}
		assignment.setAttendanceStatus(AttendanceStatus.ABSENT);
		assignment.setHoursWorked(0);
		return AssignmentResponse.from(assignmentRepository.save(assignment));
	}

	@Transactional(readOnly = true)
	public List<AssignmentResponse> findAttendanceByEvent(Long eventId) {
		if (!eventRepository.existsById(eventId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
		}
		return assignmentRepository.findByEventId(eventId).stream()
				.filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
				.map(AssignmentResponse::from)
				.toList();
	}

	@Transactional(readOnly = true)
	public VolunteerHoursResponse findHours(Long volunteerId) {
		Volunteer volunteer = volunteerRepository.findById(volunteerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Volunteer not found"));
		List<VolunteerHoursResponse.EventHours> events = assignmentRepository.findByVolunteerId(volunteerId).stream()
				.filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
				.sorted(Comparator.comparing((Assignment a) -> a.getEvent().getEventDate()).reversed())
				.map(a -> new VolunteerHoursResponse.EventHours(a.getId(), a.getEvent().getId(),
						a.getEvent().getTitle(), a.getEvent().getEventDate(), a.getAttendanceStatus(),
						a.getHoursWorked()))
				.toList();
		double total = round2(events.stream().mapToDouble(VolunteerHoursResponse.EventHours::hoursWorked).sum());
		return new VolunteerHoursResponse(volunteer.getId(), volunteer.getName(), total, events);
	}

	@Transactional(readOnly = true)
	public List<TopVolunteerResponse> findTopVolunteers(int limit) {
		// total hours per volunteer, ignoring cancelled assignments
		Map<Long, List<Assignment>> byVolunteer = new LinkedHashMap<>();
		for (Assignment a : assignmentRepository.findAll()) {
			if (a.getStatus() != AssignmentStatus.CANCELLED) {
				byVolunteer.computeIfAbsent(a.getVolunteer().getId(), k -> new java.util.ArrayList<>()).add(a);
			}
		}
		return byVolunteer.values().stream()
				.map(list -> {
					Volunteer v = list.get(0).getVolunteer();
					double total = round2(list.stream().mapToDouble(Assignment::getHoursWorked).sum());
					long worked = list.stream().filter(a -> a.getHoursWorked() > 0).count();
					return new TopVolunteerResponse(v.getId(), v.getName(), v.getEmail(), total, worked);
				})
				.filter(t -> t.totalHours() > 0)
				.sorted(Comparator.comparingDouble(TopVolunteerResponse::totalHours).reversed())
				.limit(limit)
				.toList();
	}

	private Assignment findById(Long id) {
		return assignmentRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
	}

	// Only ASSIGNED / CONFIRMED assignments on events that are not cancelled can have attendance recorded
	private void checkAttendanceAllowed(Assignment assignment) {
		if (assignment.getStatus() == AssignmentStatus.CANCELLED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"This assignment is CANCELLED. Only ASSIGNED or CONFIRMED assignments can record attendance");
		}
		if (assignment.getEvent().getStatus() == EventStatus.CANCELLED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Event \"" + assignment.getEvent().getTitle() + "\" is CANCELLED");
		}
	}

	// The action is only allowed from 30 minutes before the start time until the end time
	private void checkWithinEventWindow(Event event, LocalDateTime now, String action) {
		LocalDateTime start = LocalDateTime.of(event.getEventDate(), event.getStartTime());
		LocalDateTime opens = start.minusMinutes(EARLY_CHECK_IN_MINUTES);
		LocalDateTime end = LocalDateTime.of(event.getEventDate(), event.getEndTime());
		if (now.isBefore(opens)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, action + " opens on " + opens.toLocalDate()
					+ " at " + opens.toLocalTime() + " (" + EARLY_CHECK_IN_MINUTES + " minutes before the event starts)");
		}
		if (now.isAfter(end)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, action + " is closed: the event ended on "
					+ event.getEventDate() + " at " + event.getEndTime());
		}
	}

	private double hoursBetween(LocalDateTime from, LocalDateTime to) {
		double hours = Duration.between(from, to).toSeconds() / 3600.0;
		return round2(hours);
	}

	private double round2(double value) {
		return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
	}
}
