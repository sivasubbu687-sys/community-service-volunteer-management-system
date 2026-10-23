package com.community.volunteer_management.controller;

import com.community.volunteer_management.entity.Event;
import com.community.volunteer_management.service.EventService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/events")
public class EventController {

	private final EventService eventService;

	public EventController(EventService eventService) {
		this.eventService = eventService;
	}

	@GetMapping
	public List<Event> getAllEvents() {
		return eventService.findAll();
	}

	@GetMapping("/{id}")
	public Event getEventById(@PathVariable Long id) {
		return eventService.findById(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Event createEvent(@Valid @RequestBody Event event) {
		return eventService.create(event);
	}

	@PutMapping("/{id}")
	public Event updateEvent(@PathVariable Long id, @Valid @RequestBody Event event) {
		return eventService.update(id, event);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteEvent(@PathVariable Long id) {
		eventService.delete(id);
	}
}
