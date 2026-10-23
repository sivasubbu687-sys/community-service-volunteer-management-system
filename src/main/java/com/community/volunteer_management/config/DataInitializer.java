package com.community.volunteer_management.config;

import com.community.volunteer_management.entity.Role;
import com.community.volunteer_management.entity.User;
import com.community.volunteer_management.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Creates the default ADMIN account on first startup (only if no ADMIN exists yet)
@Component
public class DataInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	@Value("${app.admin.name:Administrator}")
	private String adminName;

	@Value("${app.admin.email}")
	private String adminEmail;

	@Value("${app.admin.password}")
	private String adminPassword;

	public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (userRepository.countByRole(Role.ADMIN) > 0) {
			// Loud reminder if an admin still uses the old well-known default password
			userRepository.findByEmail(adminEmail.trim().toLowerCase()).ifPresent(admin -> {
				if (passwordEncoder.matches("Admin@123", admin.getPassword())) {
					log.warn("SECURITY: the admin account {} still uses the old default password. Change it now "
							+ "(Change password button in the top bar).", admin.getEmail());
				}
			});
			return;
		}
		String email = adminEmail.trim().toLowerCase();
		if (userRepository.existsByEmail(email)) {
			log.warn("No ADMIN exists, but {} is already used by another user. Change app.admin.email.", email);
			return;
		}
		User admin = new User();
		admin.setName(adminName);
		admin.setEmail(email);
		admin.setPassword(passwordEncoder.encode(adminPassword));
		admin.setRole(Role.ADMIN);
		userRepository.save(admin);
		log.info("Default ADMIN account created: {}", email);
	}
}
