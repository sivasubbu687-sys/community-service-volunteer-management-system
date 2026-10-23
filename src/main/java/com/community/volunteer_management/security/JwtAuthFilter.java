package com.community.volunteer_management.security;

import com.community.volunteer_management.entity.User;
import com.community.volunteer_management.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Reads "Authorization: Bearer <token>", checks it, and logs the user in for this request only (stateless)
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

	// Request attribute read by the 401 handler to give a precise message
	public static final String AUTH_ERROR = "auth.error";

	private final JwtService jwtService;
	private final UserRepository userRepository;

	public JwtAuthFilter(JwtService jwtService, UserRepository userRepository) {
		this.jwtService = jwtService;
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			try {
				Long userId = Long.valueOf(jwtService.parse(header.substring(7)).getSubject());
				// Load the user each time so a changed role or a deleted account applies immediately
				User user = userRepository.findById(userId).orElse(null);
				if (user == null) {
					request.setAttribute(AUTH_ERROR, "This account no longer exists. Please log in again.");
				} else {
					AuthUser principal = new AuthUser(user.getId(), user.getName(), user.getEmail(), user.getRole(),
							user.getVolunteer() != null ? user.getVolunteer().getId() : null);
					var authentication = new UsernamePasswordAuthenticationToken(principal, null,
							List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
					SecurityContextHolder.getContext().setAuthentication(authentication);
				}
			} catch (ExpiredJwtException e) {
				request.setAttribute(AUTH_ERROR, "Your session has expired. Please log in again.");
			} catch (JwtException | IllegalArgumentException e) {
				request.setAttribute(AUTH_ERROR, "Invalid token. Please log in again.");
			}
		}
		chain.doFilter(request, response);
	}
}
