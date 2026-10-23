package com.community.volunteer_management.repository;

import com.community.volunteer_management.entity.Volunteer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VolunteerRepository extends JpaRepository<Volunteer, Long> {

	boolean existsByEmailIgnoreCase(String email);
}
