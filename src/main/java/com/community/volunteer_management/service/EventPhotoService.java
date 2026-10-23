package com.community.volunteer_management.service;

import com.community.volunteer_management.dto.EventPhotoInfoResponse;
import com.community.volunteer_management.entity.EventPhoto;
import com.community.volunteer_management.repository.EventPhotoRepository;
import com.community.volunteer_management.repository.EventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@Transactional
public class EventPhotoService {

	private final EventPhotoRepository photoRepository;
	private final EventRepository eventRepository;

	public EventPhotoService(EventPhotoRepository photoRepository, EventRepository eventRepository) {
		this.photoRepository = photoRepository;
		this.eventRepository = eventRepository;
	}

	public EventPhotoInfoResponse save(Long eventId, MultipartFile file) {
		if (!eventRepository.existsById(eventId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
		}
		// same checks as volunteer photos: real file type (no SVG), at most 2 MB
		PhotoService.ValidImage image = PhotoService.validate(file);

		EventPhoto photo = photoRepository.findById(eventId).orElseGet(EventPhoto::new);
		photo.setEventId(eventId);
		photo.setContentType(image.type());
		photo.setData(image.data());
		photo.setVersion(System.currentTimeMillis());
		photoRepository.save(photo);
		return new EventPhotoInfoResponse(eventId, photo.getVersion());
	}

	@Transactional(readOnly = true)
	public EventPhoto get(Long eventId) {
		return photoRepository.findById(eventId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No photo for this event"));
	}

	public void delete(Long eventId) {
		if (!photoRepository.existsById(eventId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No photo for this event");
		}
		photoRepository.deleteById(eventId);
	}

	// Used when an event is deleted (no error if there is no photo)
	public void deleteIfPresent(Long eventId) {
		if (photoRepository.existsById(eventId)) {
			photoRepository.deleteById(eventId);
		}
	}

	@Transactional(readOnly = true)
	public List<EventPhotoInfoResponse> findAllInfo() {
		return photoRepository.findAllInfo();
	}
}
