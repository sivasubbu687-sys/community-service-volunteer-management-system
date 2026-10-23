package com.community.volunteer_management.controller;

import com.community.volunteer_management.entity.LeaveNotice;
import com.community.volunteer_management.repository.LeaveNoticeRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

// "Volunteer left an event" notifications for ADMIN and COORDINATOR (see SecurityConfig)
@RestController
@RequestMapping("/api/notices")
public class NoticeController {

	private final LeaveNoticeRepository repository;

	public NoticeController(LeaveNoticeRepository repository) {
		this.repository = repository;
	}

	@GetMapping
	public List<LeaveNotice> unread() {
		return repository.findTop20ByDismissedFalseOrderByCreatedAtDesc();
	}

	@PostMapping("/{id}/dismiss")
	public void dismiss(@PathVariable Long id) {
		LeaveNotice notice = repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
		notice.setDismissed(true);
		repository.save(notice);
	}

	@PostMapping("/dismiss-all")
	public void dismissAll() {
		List<LeaveNotice> all = repository.findByDismissedFalse();
		all.forEach(n -> n.setDismissed(true));
		repository.saveAll(all);
	}
}
