package com.community.volunteer_management.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Background support: async tasks, and the scheduled job that updates event statuses
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {
}
