package com.community.volunteer_management.service;

import com.community.volunteer_management.entity.Event;
import com.community.volunteer_management.entity.EventStatus;
import com.community.volunteer_management.repository.AssignmentRepository;
import com.community.volunteer_management.repository.CategoryRepository;
import com.community.volunteer_management.repository.EventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class EventService {

	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EventService.class);

	private final EventRepository eventRepository;
	private final AssignmentRepository assignmentRepository;
	private final EventPhotoService eventPhotoService;
	private final CategoryRepository categoryRepository;

	public EventService(EventRepository eventRepository, AssignmentRepository assignmentRepository,
			EventPhotoService eventPhotoService, CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
		this.eventPhotoService = eventPhotoService;
		this.eventRepository = eventRepository;
		this.assignmentRepository = assignmentRepository;
	}

	public List<Event> findAll() {
		refreshStatuses();
		return eventRepository.findAll();
	}

	public Event findById(Long id) {
		refreshStatuses();
		return eventRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
	}

	public Event create(Event event) {
		validateTimes(event);
		event.setCategory(checkCategory(event.getCategory()));
		event.setId(null);
		if (event.getStatus() == null) {
			event.setStatus(EventStatus.UPCOMING);
		}
		return eventRepository.save(event);
	}

	public Event update(Long id, Event updatedEvent) {
		Event event = findById(id);
		validateTimes(updatedEvent);
		updatedEvent.setCategory(checkCategory(updatedEvent.getCategory()));
		boolean rescheduled = !java.util.Objects.equals(event.getEventDate(), updatedEvent.getEventDate())
				|| !java.util.Objects.equals(event.getStartTime(), updatedEvent.getStartTime())
				|| !java.util.Objects.equals(event.getEndTime(), updatedEvent.getEndTime());
		event.setTitle(updatedEvent.getTitle());
		event.setDescription(updatedEvent.getDescription());
		event.setEventDate(updatedEvent.getEventDate());
		event.setStartTime(updatedEvent.getStartTime());
		event.setEndTime(updatedEvent.getEndTime());
		event.setLocation(updatedEvent.getLocation());
		event.setRequiredVolunteers(updatedEvent.getRequiredVolunteers());
		event.setCategory(updatedEvent.getCategory());
		if (updatedEvent.getStatus() != null) {
			event.setStatus(updatedEvent.getStatus());
		}
		// Moving the date or time (for example fixing a wrong date) must not leave a stale status such as
		// COMPLETED behind: work the status out again from the new schedule, unless the event is cancelled.
		if (rescheduled && event.getStatus() != EventStatus.CANCELLED) {
			event.setStatus(statusFor(event, LocalDateTime.now()));
		}
		return eventRepository.save(event);
	}

	public void delete(Long id) {
		Event event = findById(id);
		if (assignmentRepository.existsByEventId(id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Cannot delete: this event has assignments. Remove them first.");
		}
		eventPhotoService.deleteIfPresent(id);
		eventRepository.delete(event);
	}

	// A category must be one the admin has configured (or empty); keeps the stored spelling
	private String checkCategory(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		return categoryRepository.findByNameIgnoreCase(name.trim())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Unknown category. Ask an admin to add it in Settings."))
				.getName();
	}

	private void validateTimes(Event event) {
		if (event.getStartTime() != null && event.getEndTime() != null
				&& !event.getEndTime().isAfter(event.getStartTime())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End time must be after start time");
		}
	}

	private EventStatus statusFor(Event event, LocalDateTime now) {
		if (now.isAfter(LocalDateTime.of(event.getEventDate(), event.getEndTime()))) {
			return EventStatus.COMPLETED;
		}
		if (!now.isBefore(LocalDateTime.of(event.getEventDate(), event.getStartTime()))) {
			return EventStatus.ONGOING;
		}
		return EventStatus.UPCOMING;
	}

	// Moves events along automatically: UPCOMING -> ONGOING when the start time is reached,
	// and UPCOMING / ONGOING -> COMPLETED after the end time. CANCELLED and COMPLETED are never changed.
	@Scheduled(fixedRate = 60000)
	public void refreshStatuses() {
		LocalDateTime now = LocalDateTime.now();
		for (Event event : eventRepository.findAll()) {
			if (event.getEventDate() == null || event.getStartTime() == null || event.getEndTime() == null) {
				continue;
			}
			LocalDateTime start = LocalDateTime.of(event.getEventDate(), event.getStartTime());
			LocalDateTime end = LocalDateTime.of(event.getEventDate(), event.getEndTime());
			EventStatus status = event.getStatus();
			EventStatus updated = status;
			if ((status == EventStatus.COMPLETED || status == EventStatus.ONGOING) && now.isBefore(start)) {
				// the date was moved to the future (for example a wrong date was corrected): it has not started yet
				updated = EventStatus.UPCOMING;
			} else if (status == EventStatus.UPCOMING || status == EventStatus.ONGOING) {
				if (now.isAfter(end)) {
					updated = EventStatus.COMPLETED;
				} else if (status == EventStatus.UPCOMING && !now.isBefore(start)) {
					updated = EventStatus.ONGOING;
				}
			}
			if (updated != status) {
				log.info("Event '{}' status changed automatically: {} -> {}", event.getTitle(), status, updated);
				event.setStatus(updated);
				eventRepository.save(event);
			}
		}
	}
}
