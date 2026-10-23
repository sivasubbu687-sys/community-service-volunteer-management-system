package com.community.volunteer_management.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// A volunteer's profile picture. Kept in its own table so the volunteer lists stay small and fast.
@Entity
@Table(name = "volunteer_photos")
public class VolunteerPhoto {

	@Id
	private Long volunteerId;

	@Column(nullable = false)
	private String contentType;

	@Column(nullable = false, columnDefinition = "LONGBLOB")
	private byte[] data;

	// Changes on every upload; the browser uses it to know when to download the picture again
	@Column(nullable = false)
	private long version;

	public Long getVolunteerId() {
		return volunteerId;
	}

	public void setVolunteerId(Long volunteerId) {
		this.volunteerId = volunteerId;
	}

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	public long getVersion() {
		return version;
	}

	public void setVersion(long version) {
		this.version = version;
	}
}
