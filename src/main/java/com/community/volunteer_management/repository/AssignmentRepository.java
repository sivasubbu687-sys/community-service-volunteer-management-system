package com.community.volunteer_management.repository;

import com.community.volunteer_management.entity.Assignment;
import com.community.volunteer_management.entity.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

	Optional<Assignment> findByVolunteerIdAndEventId(Long volunteerId, Long eventId);

	List<Assignment> findByEventId(Long eventId);

	List<Assignment> findByVolunteerId(Long volunteerId);

	List<Assignment> findByVolunteerIdAndStatusIn(Long volunteerId, Collection<AssignmentStatus> statuses);

	long countByEventIdAndStatusIn(Long eventId, Collection<AssignmentStatus> statuses);

	boolean existsByVolunteerId(Long volunteerId);

	// Totals for the home page statistics (cancelled assignments never count)
	@Query("select coalesce(sum(a.hoursWorked), 0) from Assignment a where a.status <> com.community.volunteer_management.entity.AssignmentStatus.CANCELLED")
	double totalHoursWorked();

	@Query("select count(a) from Assignment a where a.checkOutTime is not null and a.status <> com.community.volunteer_management.entity.AssignmentStatus.CANCELLED")
	long countCompletedShifts();

	boolean existsByEventId(Long eventId);
}
