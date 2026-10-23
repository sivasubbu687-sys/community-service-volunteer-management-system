package com.community.volunteer_management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(name = "assignments",
		uniqueConstraints = @UniqueConstraint(columnNames = { "volunteer_id", "event_id" }))
public class Assignment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "volunteer_id", nullable = false)
	private Volunteer volunteer;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false)
	private Event event;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AssignmentStatus status = AssignmentStatus.ASSIGNED;

	@Column(nullable = false)
	private LocalDateTime assignedAt = LocalDateTime.now();

	// Attendance tracking. The columns are nullable so rows created before this feature still load;
	// the getters below turn a missing value into the default (PENDING / 0 hours).
	private LocalDateTime checkInTime;

	private LocalDateTime checkOutTime;

	private Double hoursWorked = 0.0;

	@Enumerated(EnumType.STRING)
	private AttendanceStatus attendanceStatus = AttendanceStatus.PENDING;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Volunteer getVolunteer() {
		return volunteer;
	}

	public void setVolunteer(Volunteer volunteer) {
		this.volunteer = volunteer;
	}

	public Event getEvent() {
		return event;
	}

	public void setEvent(Event event) {
		this.event = event;
	}

	public AssignmentStatus getStatus() {
		return status;
	}

	public void setStatus(AssignmentStatus status) {
		this.status = status;
	}

	public LocalDateTime getAssignedAt() {
		return assignedAt;
	}

	public void setAssignedAt(LocalDateTime assignedAt) {
		this.assignedAt = assignedAt;
	}

	public LocalDateTime getCheckInTime() {
		return checkInTime;
	}

	public void setCheckInTime(LocalDateTime checkInTime) {
		this.checkInTime = checkInTime;
	}

	public LocalDateTime getCheckOutTime() {
		return checkOutTime;
	}

	public void setCheckOutTime(LocalDateTime checkOutTime) {
		this.checkOutTime = checkOutTime;
	}

	public double getHoursWorked() {
		return hoursWorked == null ? 0.0 : hoursWorked;
	}

	public void setHoursWorked(double hoursWorked) {
		this.hoursWorked = hoursWorked;
	}

	public AttendanceStatus getAttendanceStatus() {
		return attendanceStatus == null ? AttendanceStatus.PENDING : attendanceStatus;
	}

	public void setAttendanceStatus(AttendanceStatus attendanceStatus) {
		this.attendanceStatus = attendanceStatus;
	}
}
