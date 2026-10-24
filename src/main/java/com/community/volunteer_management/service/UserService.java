package com.community.volunteer_management.service;

import com.community.volunteer_management.dto.CreateUserRequest;
import com.community.volunteer_management.dto.UserResponse;
import com.community.volunteer_management.entity.Role;
import com.community.volunteer_management.entity.User;
import com.community.volunteer_management.entity.Volunteer;
import com.community.volunteer_management.repository.PasswordResetTokenRepository;
import com.community.volunteer_management.repository.UserRepository;
import com.community.volunteer_management.repository.VolunteerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// User and role management (ADMIN only, enforced in SecurityConfig)
@Service
@Transactional
public class UserService {

	private final UserRepository userRepository;
	private final VolunteerRepository volunteerRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordResetTokenRepository passwordResetTokenRepository;

	public UserService(UserRepository userRepository, VolunteerRepository volunteerRepository,
			PasswordEncoder passwordEncoder, PasswordResetTokenRepository passwordResetTokenRepository) {
		this.passwordResetTokenRepository = passwordResetTokenRepository;
		this.userRepository = userRepository;
		this.volunteerRepository = volunteerRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public List<UserResponse> findAll() {
		return userRepository.findAll().stream().map(UserResponse::from).toList();
	}

	public UserResponse create(CreateUserRequest request) {
		if (request.role() == Role.ADMIN) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New users can only be Coordinators or Volunteers");
		}
		String email = request.email().trim().toLowerCase();
		if (userRepository.existsByEmail(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "A user with this email already exists");
		}

		User user = new User();
		user.setName(request.name().trim());
		user.setEmail(email);
		user.setPassword(passwordEncoder.encode(request.password()));
		user.setRole(request.role());

		if (request.role() == Role.VOLUNTEER) {
			if (request.volunteerId() == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"volunteerId is required for a VOLUNTEER account");
			}
			user.setVolunteer(findFreeVolunteer(request.volunteerId()));
		}
		return UserResponse.from(userRepository.save(user));
	}

	public UserResponse updateRole(Long id, Role role, Long currentUserId) {
		if (id.equals(currentUserId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot change your own role");
		}
		User user = findById(id);
		if (role == Role.VOLUNTEER && user.getVolunteer() == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"This user has no volunteer profile, so it cannot become a VOLUNTEER");
		}
		user.setRole(role);
		return UserResponse.from(userRepository.save(user));
	}

	public void delete(Long id, Long currentUserId) {
		if (id.equals(currentUserId)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete your own account");
		}
		User user = findById(id);
		passwordResetTokenRepository.deleteByUserId(id);
		userRepository.delete(user);
	}

	private User findById(Long id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
	}

	private Volunteer findFreeVolunteer(Long volunteerId) {
		Volunteer volunteer = volunteerRepository.findById(volunteerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Volunteer not found"));
		if (userRepository.existsByVolunteerId(volunteerId)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "This volunteer already has a login account");
		}
		return volunteer;
	}
}
