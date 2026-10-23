package com.community.volunteer_management.controller;

import com.community.volunteer_management.dto.EventPhotoInfoResponse;
import com.community.volunteer_management.entity.EventPhoto;
import com.community.volunteer_management.service.EventPhotoService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

// Everyone logged in can see event pictures; only ADMIN and COORDINATOR change them (see SecurityConfig).
@RestController
@RequestMapping("/api/events")
public class EventPhotoController {

	private final EventPhotoService photoService;

	public EventPhotoController(EventPhotoService photoService) {
		this.photoService = photoService;
	}

	// Which events have a picture
	@GetMapping("/photos")
	public List<EventPhotoInfoResponse> list() {
		return photoService.findAllInfo();
	}

	@GetMapping("/{id}/photo")
	public ResponseEntity<byte[]> get(@PathVariable Long id) {
		EventPhoto photo = photoService.get(id);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(photo.getContentType()))
				// the page asks with ?v=<version>, so a new upload gets a new address
				.cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
				.body(photo.getData());
	}

	@PutMapping("/{id}/photo")
	public EventPhotoInfoResponse upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
		return photoService.save(id, file);
	}

	@DeleteMapping("/{id}/photo")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		photoService.delete(id);
	}
}
