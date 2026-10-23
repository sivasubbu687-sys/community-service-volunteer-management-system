package com.community.volunteer_management.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

// Turns errors into JSON responses that always contain a readable "message"
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
		String message = errors.values().stream().collect(Collectors.joining("; "));
		return body(HttpStatus.BAD_REQUEST, message, errors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
		return body(HttpStatus.BAD_REQUEST,
				"Invalid request body. Check the JSON format: dates must be yyyy-MM-dd, times HH:mm (or HH:mm:ss), "
						+ "ids must be numbers, and status values must be one of the allowed names.",
				Map.of());
	}

	// A file bigger than spring.servlet.multipart.max-file-size
	@ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
	public ResponseEntity<Map<String, Object>> handleTooLarge(
			org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
		return body(HttpStatus.PAYLOAD_TOO_LARGE, "The photo is too large. The limit is 2 MB.", Map.of());
	}

	// 404 / 409 / 400 thrown by the services (Spring hides their reason text by default)
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException ex) {
		String message = ex.getReason() != null ? ex.getReason() : "Request failed";
		return body(ex.getStatusCode(), message, Map.of());
	}

	private ResponseEntity<Map<String, Object>> body(HttpStatusCode status, String message, Map<String, String> errors) {
		HttpStatus resolved = HttpStatus.resolve(status.value());
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", status.value());
		body.put("error", resolved != null ? resolved.getReasonPhrase() : "Error");
		body.put("message", message);
		body.put("errors", errors);
		return ResponseEntity.status(status).body(body);
	}
}
