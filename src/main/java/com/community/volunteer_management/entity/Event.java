package com.community.volunteer_management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "events")
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank(message = "Title is required")
	@Column(nullable = false)
	private String title;

	@Column(length = 1000)
	private String description;

	@NotNull(message = "Event date is required")
	@Column(nullable = false)
	private LocalDate eventDate;

	@NotNull(message = "Start time is required")
	@Column(nullable = false)
	private LocalTime startTime;

	@NotNull(message = "End time is required")
	@Column(nullable = false)
	private LocalTime endTime;

	@NotBlank(message = "Location is required")
	@Column(nullable = false)
	private String location;

	@Positive(message = "Required volunteers must be greater than 0")
	@Column(nullable = false)
	private int requiredVolunteers;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EventStatus status = EventStatus.UPCOMING;

	// Optional: older events and events created through the API without it simply have no label
	@Column(length = 60)
	private String category;

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public LocalDate getEventDate() {
		return eventDate;
	}

	public void setEventDate(LocalDate eventDate) {
		this.eventDate = eventDate;
	}

	public LocalTime getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalTime startTime) {
		this.startTime = startTime;
	}

	public LocalTime getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalTime endTime) {
		this.endTime = endTime;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public int getRequiredVolunteers() {
		return requiredVolunteers;
	}

	public void setRequiredVolunteers(int requiredVolunteers) {
		this.requiredVolunteers = requiredVolunteers;
	}

	public EventStatus getStatus() {
		return status;
	}

	public void setStatus(EventStatus status) {
		this.status = status;
	}
}
