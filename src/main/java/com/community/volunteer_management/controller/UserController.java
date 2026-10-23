package com.community.volunteer_management.controller;

import com.community.volunteer_management.dto.CreateUserRequest;
import com.community.volunteer_management.dto.RoleUpdateRequest;
import com.community.volunteer_management.dto.UserResponse;
import com.community.volunteer_management.security.AuthUser;
import com.community.volunteer_management.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public List<UserResponse> getAllUsers() {
		return userService.findAll();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
		return userService.create(request);
	}

	@PutMapping("/{id}/role")
	public UserResponse updateRole(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request,
			@AuthenticationPrincipal AuthUser currentUser) {
		return userService.updateRole(id, request.role(), currentUser.id());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteUser(@PathVariable Long id, @AuthenticationPrincipal AuthUser currentUser) {
		userService.delete(id, currentUser.id());
	}
}
