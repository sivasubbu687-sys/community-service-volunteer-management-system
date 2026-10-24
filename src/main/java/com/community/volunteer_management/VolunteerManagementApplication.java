package com.community.volunteer_management;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VolunteerManagementApplication {

	public static void main(String[] args) {
		// Event times are local wall-clock times; cloud servers run in UTC, so pin the zone (override with APP_TIMEZONE)
		String zone = System.getenv("APP_TIMEZONE");
		TimeZone.setDefault(TimeZone.getTimeZone(zone == null || zone.isBlank() ? "Asia/Kolkata" : zone));
		SpringApplication.run(VolunteerManagementApplication.class, args);
	}

}
