package com.community.volunteer_management.service;

import com.community.volunteer_management.dto.PhotoInfoResponse;
import com.community.volunteer_management.entity.VolunteerPhoto;
import com.community.volunteer_management.repository.VolunteerPhotoRepository;
import com.community.volunteer_management.repository.VolunteerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@Service
@Transactional
public class PhotoService {

	private static final long MAX_BYTES = 2 * 1024 * 1024;   // 2 MB

	private final VolunteerPhotoRepository photoRepository;
	private final VolunteerRepository volunteerRepository;

	public PhotoService(VolunteerPhotoRepository photoRepository, VolunteerRepository volunteerRepository) {
		this.photoRepository = photoRepository;
		this.volunteerRepository = volunteerRepository;
	}

	public PhotoInfoResponse save(Long volunteerId, MultipartFile file) {
		if (!volunteerRepository.existsById(volunteerId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Volunteer not found");
		}
		ValidImage image = validate(file);

		VolunteerPhoto photo = photoRepository.findById(volunteerId).orElseGet(VolunteerPhoto::new);
		photo.setVolunteerId(volunteerId);
		photo.setContentType(image.type());
		photo.setData(image.data());
		photo.setVersion(System.currentTimeMillis());
		photoRepository.save(photo);
		return new PhotoInfoResponse(volunteerId, photo.getVersion());
	}

	@Transactional(readOnly = true)
	public VolunteerPhoto get(Long volunteerId) {
		return photoRepository.findById(volunteerId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No photo for this volunteer"));
	}

	public void delete(Long volunteerId) {
		if (!photoRepository.existsById(volunteerId)) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No photo for this volunteer");
		}
		photoRepository.deleteById(volunteerId);
	}

	// Used when a volunteer is deleted (no error if there is no photo)
	public void deleteIfPresent(Long volunteerId) {
		if (photoRepository.existsById(volunteerId)) {
			photoRepository.deleteById(volunteerId);
		}
	}

	@Transactional(readOnly = true)
	public List<PhotoInfoResponse> findAllInfo() {
		return photoRepository.findAllInfo();
	}

	@Transactional(readOnly = true)
	public List<PhotoInfoResponse> findInfoFor(Long volunteerId) {
		return photoRepository.findById(volunteerId)
				.map(p -> List.of(new PhotoInfoResponse(p.getVolunteerId(), p.getVersion())))
				.orElse(List.of());
	}

	// A checked picture: its bytes and the type found from the real content
	public record ValidImage(byte[] data, String type) {
	}

	// Shared by volunteer and event photos. The type is decided by the file's real content, never by its
	// name or what the browser claims. SVG is deliberately not allowed (it can carry scripts).
	public static ValidImage validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please choose a photo to upload");
		}
		if (file.getSize() > MAX_BYTES) {
			throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "The photo is too large. The limit is 2 MB.");
		}
		byte[] data;
		try {
			data = file.getBytes();
		} catch (IOException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The photo could not be read");
		}
		String type = detectType(data);
		if (type == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Only JPG, PNG, GIF or WebP pictures are allowed");
		}
		return new ValidImage(data, type);
	}

	private static String detectType(byte[] d) {
		if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
			return "image/jpeg";
		}
		if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
			return "image/png";
		}
		if (d.length >= 6 && d[0] == 'G' && d[1] == 'I' && d[2] == 'F' && d[3] == '8') {
			return "image/gif";
		}
		if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
				&& d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
			return "image/webp";
		}
		return null;
	}
}
