package com.community.volunteer_management.repository;

import com.community.volunteer_management.entity.LeaveNotice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveNoticeRepository extends JpaRepository<LeaveNotice, Long> {

	List<LeaveNotice> findTop20ByDismissedFalseOrderByCreatedAtDesc();

	List<LeaveNotice> findByDismissedFalse();
}
