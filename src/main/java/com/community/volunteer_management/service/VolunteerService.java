package com.community.volunteer_management.service;

import com.community.volunteer_management.entity.Volunteer;
import com.community.volunteer_management.repository.AssignmentRepository;
import com.community.volunteer_management.repository.PasswordResetTokenRepository;
import com.community.volunteer_management.repository.UserRepository;
import com.community.volunteer_management.repository.VolunteerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class VolunteerService {

	private final VolunteerRepository volunteerRepository;
	private final AssignmentRepository assignmentRepository;
	private final UserRepository userRepository;
	private final PhotoService photoService;
	private final PasswordResetTokenRepository passwordResetTokenRepository;

	public VolunteerService(VolunteerRepository volunteerRepository, AssignmentRepository assignmentRepository,
			UserRepository userRepository, PasswordResetTokenRepository passwordResetTokenRepository,
			PhotoService photoService) {
		this.photoService = photoService;
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.userRepository = userRepository;
		this.volunteerRepository = volunteerRepository;
		this.assignmentRepository = assignmentRepository;
	}

	public List<Volunteer> findAll() {
		return volunteerRepository.findAll();
	}

	public Volunteer findById(Long id) {
		return volunteerRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Volunteer not found"));
	}

	public Volunteer create(Volunteer volunteer) {
		return volunteerRepository.save(volunteer);
	}

	public Volunteer update(Long id, Volunteer updatedVolunteer) {
		Volunteer volunteer = findById(id);
		volunteer.setName(updatedVolunteer.getName());
		volunteer.setEmail(updatedVolunteer.getEmail());
		volunteer.setPhone(updatedVolunteer.getPhone());
		return volunteerRepository.save(volunteer);
	}

	@Transactional
	public void delete(Long id) {
		Volunteer volunteer = findById(id);
		if (assignmentRepository.existsByVolunteerId(id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Cannot delete: this volunteer has assignments. Remove them first.");
		}
		// the volunteer's login account (if any) goes with the profile
		userRepository.findByVolunteerId(id).ifPresent(user -> {
			passwordResetTokenRepository.deleteByUserId(user.getId());
			userRepository.delete(user);
		});
		photoService.deleteIfPresent(id);
		volunteerRepository.delete(volunteer);
	}
}