package com.community.volunteer_management.repository;

import com.community.volunteer_management.dto.EventPhotoInfoResponse;
import com.community.volunteer_management.entity.EventPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EventPhotoRepository extends JpaRepository<EventPhoto, Long> {

	// Without the picture bytes
	@Query("select new com.community.volunteer_management.dto.EventPhotoInfoResponse(p.eventId, p.version) from EventPhoto p")
	List<EventPhotoInfoResponse> findAllInfo();
}
