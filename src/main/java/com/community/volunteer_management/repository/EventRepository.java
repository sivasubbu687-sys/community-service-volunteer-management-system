package com.community.volunteer_management.repository;

import com.community.volunteer_management.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

	long countByCategoryIgnoreCase(String category);

	java.util.List<Event> findByCategoryIgnoreCase(String category);
}
