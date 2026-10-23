package com.community.volunteer_management.controller;

import com.community.volunteer_management.entity.Volunteer;
import com.community.volunteer_management.service.VolunteerService;
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
@RequestMapping("/api/volunteers")
public class VolunteerController {

	private final VolunteerService volunteerService;

	public VolunteerController(VolunteerService volunteerService) {
		this.volunteerService = volunteerService;
	}

	@GetMapping
	public List<Volunteer> getAllVolunteers() {
		return volunteerService.findAll();
	}

	@GetMapping("/{id}")
	public Volunteer getVolunteerById(@PathVariable Long id) {
		return volunteerService.findById(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public Volunteer createVolunteer(@RequestBody Volunteer volunteer) {
		return volunteerService.create(volunteer);
	}

	@PutMapping("/{id}")
	public Volunteer updateVolunteer(@PathVariable Long id, @RequestBody Volunteer volunteer) {
		return volunteerService.update(id, volunteer);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteVolunteer(@PathVariable Long id) {
		volunteerService.delete(id);
	}
}