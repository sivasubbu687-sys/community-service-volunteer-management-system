package com.community.volunteer_management.controller;

import com.community.volunteer_management.dto.AuthResponse;
import com.community.volunteer_management.dto.ChangePasswordRequest;
import com.community.volunteer_management.dto.ForgotPasswordRequest;
import com.community.volunteer_management.dto.LoginRequest;
import com.community.volunteer_management.dto.MessageResponse;
import com.community.volunteer_management.dto.RegisterRequest;
import com.community.volunteer_management.dto.ResetPasswordRequest;
import com.community.volunteer_management.dto.UserResponse;
import com.community.volunteer_management.repository.UserRepository;
import com.community.volunteer_management.security.AuthUser;
import com.community.volunteer_management.service.AuthService;
import com.community.volunteer_management.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final PasswordResetService passwordResetService;
	private final UserRepository userRepository;

	public AuthController(AuthService authService, PasswordResetService passwordResetService,
			UserRepository userRepository) {
		this.authService = authService;
		this.passwordResetService = passwordResetService;
		this.userRepository = userRepository;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
		return authService.register(request);
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	// Public. Always answers with the same message, so it never reveals which emails are registered.
	@PostMapping("/forgot-password")
	public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
		passwordResetService.requestReset(request.email(), http.getRemoteAddr());
		return new MessageResponse("If that email is registered, a reset link has been sent.");
	}

	// Public. The raw token from the emailed link is the proof of ownership.
	@PostMapping("/reset-password")
	public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
		passwordResetService.resetPassword(request.token(), request.newPassword());
		return new MessageResponse("Your password has been reset. You can now log in.");
	}

	@PutMapping("/password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@Valid @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal AuthUser user) {
		authService.changePassword(user.id(), request);
	}

	// The currently logged-in user
	@GetMapping("/me")
	public UserResponse me(@AuthenticationPrincipal AuthUser user) {
		return userRepository.findById(user.id()).map(UserResponse::from).orElseThrow();
	}
}
