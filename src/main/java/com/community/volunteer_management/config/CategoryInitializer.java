package com.community.volunteer_management.config;

import com.community.volunteer_management.entity.Category;
import com.community.volunteer_management.repository.CategoryRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Moves events.category from the old fixed list to admin-managed names, and seeds the default categories
@Component
public class CategoryInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(CategoryInitializer.class);

	private static final List<String> COLORS = List.of("#2F6FED", "#22A66B", "#F5832B", "#7C5CD6", "#14B8A6", "#C5CCD6");

	private static final List<String> DEFAULTS = List.of("Environment", "Health", "Cleanliness", "Social Welfare",
			"Education", "Other");

	private final CategoryRepository categoryRepository;
	private final JdbcTemplate jdbc;

	public CategoryInitializer(CategoryRepository categoryRepository, JdbcTemplate jdbc) {
		this.categoryRepository = categoryRepository;
		this.jdbc = jdbc;
	}

	@Override
	public void run(ApplicationArguments args) {
		try {
			// Hibernate may have created the old column as a MySQL ENUM; make it free text
			jdbc.execute("ALTER TABLE events MODIFY category VARCHAR(60) NULL");
		} catch (Exception e) {
			log.warn("Could not alter events.category (already converted?): {}", e.getMessage());
		}
		if (categoryRepository.count() == 0) {
			for (String name : DEFAULTS) {
				Category c = new Category();
				c.setName(name);
				categoryRepository.save(c);
			}
		}
		// Give every category without a colour one from the standard palette
		List<Category> all = categoryRepository.findAllByOrderByIdAsc();
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).getColor() == null) {
				all.get(i).setColor(COLORS.get(i % COLORS.size()));
				categoryRepository.save(all.get(i));
			}
		}
		// Old rows stored names like SOCIAL_WELFARE
		for (String name : DEFAULTS) {
			jdbc.update("UPDATE events SET category = ? WHERE category = ?", name,
					name.toUpperCase().replace(' ', '_'));
		}
	}
}
