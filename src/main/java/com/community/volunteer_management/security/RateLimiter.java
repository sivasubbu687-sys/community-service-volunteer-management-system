package com.community.volunteer_management.security;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Simple in-memory "N requests per time window" limiter (sliding window).
// It lives in memory, so counters reset when the application restarts.
@Component
public class RateLimiter {

	private static final Duration LONGEST_WINDOW = Duration.ofHours(1);

	private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();

	// Records one request for the key. Returns false when the key already used up its quota in the window.
	public boolean tryAcquire(String key, int maxRequests, Duration window) {
		Deque<Instant> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
		Instant now = Instant.now();
		synchronized (times) {
			while (!times.isEmpty() && times.peekFirst().isBefore(now.minus(window))) {
				times.pollFirst();
			}
			if (times.size() >= maxRequests) {
				return false;
			}
			times.addLast(now);
			return true;
		}
	}

	// Forget keys that have been quiet for a whole window, so the map cannot grow forever
	@Scheduled(fixedRate = 3600000)
	public void purge() {
		Instant cutoff = Instant.now().minus(LONGEST_WINDOW);
		hits.entrySet().removeIf(entry -> {
			synchronized (entry.getValue()) {
				return entry.getValue().isEmpty() || entry.getValue().peekLast().isBefore(cutoff);
			}
		});
	}
}
