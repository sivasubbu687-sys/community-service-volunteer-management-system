package com.community.volunteer_management.service;

import com.community.volunteer_management.dto.AuthResponse;
import com.community.volunteer_management.dto.ChangePasswordRequest;
import com.community.volunteer_management.dto.LoginRequest;
import com.community.volunteer_management.dto.RegisterRequest;
import com.community.volunteer_management.entity.Role;
import com.community.volunteer_management.entity.User;
import com.community.volunteer_management.entity.Volunteer;
import com.community.volunteer_management.repository.UserRepository;
import com.community.volunteer_management.repository.VolunteerRepository;
import com.community.volunteer_management.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class AuthService {

	private final UserRepository userRepository;
	private final VolunteerRepository volunteerRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(UserRepository userRepository, VolunteerRepository volunteerRepository,
			PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.volunteerRepository = volunteerRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	// Self-registration always creates a VOLUNTEER account plus its linked Volunteer profile
	public AuthResponse register(RegisterRequest request) {
		String email = request.email().trim().toLowerCase();
		if (userRepository.existsByEmail(email) || volunteerRepository.existsByEmailIgnoreCase(email)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"This email address is already registered. Please log in, or ask an administrator to link your account.");
		}

		Volunteer volunteer = new Volunteer();
		volunteer.setName(request.name().trim());
		volunteer.setEmail(email);
		volunteer.setPhone(PhoneRules.check(request.phone()));
		volunteer = volunteerRepository.save(volunteer);

		User user = new User();
		user.setName(volunteer.getName());
		user.setEmail(email);
		user.setPassword(passwordEncoder.encode(request.password()));
		user.setRole(Role.VOLUNTEER);
		user.setVolunteer(volunteer);
		return toResponse(userRepository.save(user));
	}

	public AuthResponse login(LoginRequest request) {
		User user = userRepository.findByEmail(request.email().trim().toLowerCase())
				.filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
		return toResponse(user);
	}

	// Google already proved the email address. An existing account with that email is logged in;
	// otherwise a new VOLUNTEER account is created (with an unusable random password).
	public AuthResponse loginWithGoogle(String rawEmail, String name) {
		String email = rawEmail.trim().toLowerCase();
		User user = userRepository.findByEmail(email).orElse(null);
		if (user == null) {
			Volunteer volunteer = volunteerRepository.findAll().stream()
					.filter(v -> email.equalsIgnoreCase(v.getEmail())).findFirst().orElse(null);
			if (volunteer == null) {
				volunteer = new Volunteer();
				volunteer.setName(name == null || name.isBlank() ? email : name.trim());
				volunteer.setEmail(email);
				volunteer.setPhone("Not provided");
				volunteer = volunteerRepository.save(volunteer);
			}
			user = new User();
			user.setName(volunteer.getName());
			user.setEmail(email);
			user.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
			user.setRole(Role.VOLUNTEER);
			user.setVolunteer(volunteer);
			user = userRepository.save(user);
		}
		return toResponse(user);
	}

	public void changePassword(Long userId, ChangePasswordRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
		if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
			// 400 (not 401) so the screen shows the message instead of treating it as an expired login
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
		}
		if (request.currentPassword().equals(request.newPassword())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"The new password must be different from the current password");
		}
		user.setPassword(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);
	}

	private AuthResponse toResponse(User user) {
		return new AuthResponse(jwtService.generateToken(user), user.getId(), user.getName(), user.getEmail(),
				user.getRole(), user.getVolunteer() != null ? user.getVolunteer().getId() : null);
	}
}
