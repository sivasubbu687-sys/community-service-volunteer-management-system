package com.community.volunteer_management.repository;

import com.community.volunteer_management.dto.PhotoInfoResponse;
import com.community.volunteer_management.entity.VolunteerPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface VolunteerPhotoRepository extends JpaRepository<VolunteerPhoto, Long> {

	// Without the picture bytes
	@Query("select new com.community.volunteer_management.dto.PhotoInfoResponse(p.volunteerId, p.version) from VolunteerPhoto p")
	List<PhotoInfoResponse> findAllInfo();
}
