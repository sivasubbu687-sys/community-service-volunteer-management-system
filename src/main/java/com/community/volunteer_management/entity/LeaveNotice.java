package com.community.volunteer_management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

// Shown to admins and coordinators when a volunteer leaves an event, with the reason they gave
@Entity
@Table(name = "leave_notices")
public class LeaveNotice {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String volunteerName;

	private Long eventId;

	@Column(nullable = false)
	private String eventTitle;

	@Column(nullable = false, length = 500)
	private String reason;

	@Column(nullable = false)
	private LocalDateTime createdAt = LocalDateTime.now();

	// Staff dismiss a notice once they have read it
	@Column(nullable = false)
	private boolean dismissed;

	public Long getId() {
		return id;
	}

	public String getVolunteerName() {
		return volunteerName;
	}

	public void setVolunteerName(String volunteerName) {
		this.volunteerName = volunteerName;
	}

	public Long getEventId() {
		return eventId;
	}

	public void setEventId(Long eventId) {
		this.eventId = eventId;
	}

	public String getEventTitle() {
		return eventTitle;
	}

	public void setEventTitle(String eventTitle) {
		this.eventTitle = eventTitle;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public boolean isDismissed() {
		return dismissed;
	}

	public void setDismissed(boolean dismissed) {
		this.dismissed = dismissed;
	}
}
