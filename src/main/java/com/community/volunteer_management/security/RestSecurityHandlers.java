package com.community.volunteer_management.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// JSON error bodies for security failures (same shape as ApiExceptionHandler): 401 and 403
@Component
public class RestSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

	// 401: no token, invalid token or expired token
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
			throws IOException {
		Object reason = request.getAttribute(JwtAuthFilter.AUTH_ERROR);
		String message = reason != null ? reason.toString() : "Authentication required. Please log in.";
		write(response, HttpStatus.UNAUTHORIZED, message);
	}

	// 403: logged in, but the role is not allowed
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
			throws IOException {
		write(response, HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
	}

	private void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		String json = "{\"status\":" + status.value() + ",\"error\":\"" + status.getReasonPhrase()
				+ "\",\"message\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"errors\":{}}";
		response.getWriter().write(json);
	}
}
