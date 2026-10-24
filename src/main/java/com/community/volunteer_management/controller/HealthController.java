package com.community.volunteer_management.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Public, cheap endpoint for uptime monitors (keeps a free host awake); touches no data
@RestController
public class HealthController {

	@GetMapping("/api/health")
	public Map<String, String> health() {
		return Map.of("status", "ok");
	}
}
