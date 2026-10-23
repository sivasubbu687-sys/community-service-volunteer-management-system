package com.community.volunteer_management.controller;

import com.community.volunteer_management.dto.PhotoInfoResponse;
import com.community.volunteer_management.entity.Role;
import com.community.volunteer_management.entity.VolunteerPhoto;
import com.community.volunteer_management.security.AuthUser;
import com.community.volunteer_management.service.PhotoService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

// Who may do what is decided in SecurityConfig: a volunteer only touches their own photo,
// ADMIN can manage any, COORDINATOR can view any.
@RestController
@RequestMapping("/api/volunteers")
public class PhotoController {

	private final PhotoService photoService;

	public PhotoController(PhotoService photoService) {
		this.photoService = photoService;
	}

	// Which volunteers have a photo (staff: everyone, volunteer: only themselves)
	@GetMapping("/photos")
	public List<PhotoInfoResponse> list(@AuthenticationPrincipal AuthUser user) {
		if (user.role() == Role.VOLUNTEER) {
			return user.volunteerId() == null ? List.of() : photoService.findInfoFor(user.volunteerId());
		}
		return photoService.findAllInfo();
	}

	@GetMapping("/{id}/photo")
	public ResponseEntity<byte[]> get(@PathVariable Long id) {
		VolunteerPhoto photo = photoService.get(id);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(photo.getContentType()))
				// the page asks with ?v=<version>, so a new upload gets a new address
				.cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePrivate())
				.body(photo.getData());
	}

	@PutMapping("/{id}/photo")
	public PhotoInfoResponse upload(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
		return photoService.save(id, file);
	}

	@DeleteMapping("/{id}/photo")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		photoService.delete(id);
	}
}
