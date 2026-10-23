package com.community.volunteer_management.service;

import com.community.volunteer_management.dto.AssignmentRequest;
import com.community.volunteer_management.dto.AssignmentResponse;
import com.community.volunteer_management.dto.EventSpotsResponse;
import com.community.volunteer_management.entity.Assignment;
import com.community.volunteer_management.entity.AssignmentStatus;
import com.community.volunteer_management.entity.Event;
import com.community.volunteer_management.entity.EventStatus;
import com.community.volunteer_management.entity.LeaveNotice;
import com.community.volunteer_management.entity.Volunteer;
import com.community.volunteer_management.repository.AssignmentRepository;
import com.community.volunteer_management.repository.EventRepository;
import com.community.volunteer_management.repository.LeaveNoticeRepository;
import com.community.volunteer_management.repository.VolunteerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class AssignmentService {

	// An assignment that counts towards capacity and schedule conflicts
	private static final List<AssignmentStatus> ACTIVE = List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.CONFIRMED);

	private final AssignmentRepository assignmentRepository;
	private final VolunteerRepository volunteerRepository;
	private final EventRepository eventRepository;
	private final EmailService emailService;
	private final LeaveNoticeRepository leaveNoticeRepository;

	public AssignmentService(AssignmentRepository assignmentRepository, VolunteerRepository volunteerRepository,
			EventRepository eventRepository, EmailService emailService,
			LeaveNoticeRepository leaveNoticeRepository) {
		this.leaveNoticeRepository = leaveNoticeRepository;
		this.emailService = emailService;
		this.assignmentRepository = assignmentRepository;
		this.volunteerRepository = volunteerRepository;
		this.eventRepository = eventRepository;
	}

	@Transactional(readOnly = true)
	public List<AssignmentResponse> findAll() {
		return assignmentRepository.findAll().stream().map(AssignmentResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public List<AssignmentResponse> findByEvent(Long eventId) {
		if (!eventRepository.existsById(eventId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
		}
		return assignmentRepository.findByEventId(eventId).stream().map(AssignmentResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public List<AssignmentResponse> findByVolunteer(Long volunteerId) {
		if (!volunteerRepository.existsById(volunteerId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Volunteer not found");
		}
		return assignmentRepository.findByVolunteerId(volunteerId).stream().map(AssignmentResponse::from).toList();
	}

	public AssignmentResponse create(AssignmentRequest request) {
		Volunteer volunteer = volunteerRepository.findById(request.volunteerId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Volunteer not found"));
		Event event = eventRepository.findById(request.eventId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

		Assignment existing = assignmentRepository.findByVolunteerIdAndEventId(volunteer.getId(), event.getId())
				.orElse(null);
		if (existing != null && existing.getStatus() != AssignmentStatus.CANCELLED) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					volunteer.getName() + " is already assigned to \"" + event.getTitle() + "\"");
		}

		checkCanActivate(volunteer, event, existing);

		// The pair (volunteer, event) is unique, so a cancelled assignment is re-activated instead of duplicated
		Assignment assignment = existing != null ? existing : new Assignment();
		assignment.setVolunteer(volunteer);
		assignment.setEvent(event);
		assignment.setStatus(AssignmentStatus.ASSIGNED);
		assignment.setAssignedAt(LocalDateTime.now());
		Assignment saved = assignmentRepository.save(assignment);
		// Send the email only after the assignment is safely committed (in the background)
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				emailService.sendAssignmentEmail(volunteer, event);
			}
		});
		return AssignmentResponse.from(saved)
				.withEmailStatus("A notification email is being sent to " + volunteer.getEmail() + ".");
	}

	// A volunteer joins an open event themselves (same conflict, capacity and event-status rules as an assignment)
	public AssignmentResponse join(Long eventId, Long volunteerId) {
		return create(new AssignmentRequest(volunteerId, eventId));
	}

	// A volunteer leaves an event they joined, as long as they have not checked in yet
	public AssignmentResponse leave(Long eventId, Long volunteerId, String reason) {
		String cleanReason = reason == null ? "" : reason.trim();
		if (cleanReason.length() < 3) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please tell us why you are leaving this event");
		}
		if (cleanReason.length() > 500) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The reason must be 500 characters or fewer");
		}
		Assignment assignment = assignmentRepository.findByVolunteerIdAndEventId(volunteerId, eventId)
				.filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"You are not assigned to this event"));
		if (assignment.getCheckInTime() != null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"You have already checked in, so you cannot leave this event");
		}
		assignment.setStatus(AssignmentStatus.CANCELLED);
		LeaveNotice notice = new LeaveNotice();
		notice.setVolunteerName(assignment.getVolunteer().getName());
		notice.setEventId(assignment.getEvent().getId());
		notice.setEventTitle(assignment.getEvent().getTitle());
		notice.setReason(cleanReason);
		leaveNoticeRepository.save(notice);
		return AssignmentResponse.from(assignmentRepository.save(assignment));
	}

	// Number of active volunteers per event (counts only, no personal details)
	@Transactional(readOnly = true)
	public List<EventSpotsResponse> findSpots() {
		return assignmentRepository.findAll().stream()
				.filter(a -> ACTIVE.contains(a.getStatus()))
				.collect(java.util.stream.Collectors.groupingBy(a -> a.getEvent().getId(),
						java.util.stream.Collectors.counting()))
				.entrySet().stream()
				.map(e -> new EventSpotsResponse(e.getKey(), e.getValue()))
				.toList();
	}

	public AssignmentResponse updateStatus(Long id, AssignmentStatus newStatus) {
		Assignment assignment = findById(id);
		boolean reactivating = assignment.getStatus() == AssignmentStatus.CANCELLED
				&& newStatus != AssignmentStatus.CANCELLED;
		if (reactivating) {
			checkCanActivate(assignment.getVolunteer(), assignment.getEvent(), assignment);
		}
		assignment.setStatus(newStatus);
		return AssignmentResponse.from(assignmentRepository.save(assignment));
	}

	public void delete(Long id) {
		assignmentRepository.delete(findById(id));
	}

	private Assignment findById(Long id) {
		return assignmentRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
	}

	// Business rules for making a volunteer active on an event
	// "self" is the assignment being re-activated (if any), so it is ignored in the conflict check
	private void checkCanActivate(Volunteer volunteer, Event event, Assignment self) {
		if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Cannot assign volunteers to a " + event.getStatus() + " event");
		}

		long taken = assignmentRepository.countByEventIdAndStatusIn(event.getId(), ACTIVE);
		if (taken >= event.getRequiredVolunteers()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Event \"" + event.getTitle() + "\" is full (" + taken + " of "
							+ event.getRequiredVolunteers() + " volunteers assigned)");
		}

		for (Assignment other : assignmentRepository.findByVolunteerIdAndStatusIn(volunteer.getId(), ACTIVE)) {
			Event otherEvent = other.getEvent();
			if (self != null && other.getId().equals(self.getId())) {
				continue;
			}
			if (otherEvent.getStatus() == EventStatus.CANCELLED) {
				continue;
			}
			boolean sameDate = otherEvent.getEventDate().equals(event.getEventDate());
			boolean overlaps = otherEvent.getStartTime().isBefore(event.getEndTime())
					&& event.getStartTime().isBefore(otherEvent.getEndTime());
			if (sameDate && overlaps) {
				throw new ResponseStatusException(HttpStatus.CONFLICT,
						"Schedule conflict: " + volunteer.getName() + " is already assigned to \""
								+ otherEvent.getTitle() + "\" on " + otherEvent.getEventDate() + " from "
								+ otherEvent.getStartTime() + " to " + otherEvent.getEndTime());
			}
		}
	}
}
