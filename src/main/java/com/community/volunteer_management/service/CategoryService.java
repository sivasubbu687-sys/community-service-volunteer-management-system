package com.community.volunteer_management.service;

import com.community.volunteer_management.entity.Category;
import com.community.volunteer_management.entity.Event;
import com.community.volunteer_management.repository.CategoryRepository;
import com.community.volunteer_management.repository.EventRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CategoryService {

	private final CategoryRepository categoryRepository;
	private final EventRepository eventRepository;

	public CategoryService(CategoryRepository categoryRepository, EventRepository eventRepository) {
		this.categoryRepository = categoryRepository;
		this.eventRepository = eventRepository;
	}

	public List<Category> findAll() {
		return categoryRepository.findAllByOrderByIdAsc();
	}

	public Category create(String name, String color) {
		String clean = clean(name);
		if (categoryRepository.findByNameIgnoreCase(clean).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "That category already exists");
		}
		Category category = new Category();
		category.setName(clean);
		category.setColor(cleanColor(color));
		return categoryRepository.save(category);
	}

	// Renaming also relabels the events that already use the category
	@Transactional
	public Category update(Long id, String name, String color) {
		Category category = find(id);
		String clean = clean(name);
		categoryRepository.findByNameIgnoreCase(clean).ifPresent(other -> {
			if (!other.getId().equals(id)) {
				throw new ResponseStatusException(HttpStatus.CONFLICT, "That category already exists");
			}
		});
		List<Event> events = eventRepository.findByCategoryIgnoreCase(category.getName());
		category.setName(clean);
		if (color != null) {
			category.setColor(cleanColor(color));
		}
		categoryRepository.save(category);
		events.forEach(e -> e.setCategory(clean));
		eventRepository.saveAll(events);
		return category;
	}

	public void delete(Long id) {
		Category category = find(id);
		long used = eventRepository.countByCategoryIgnoreCase(category.getName());
		if (used > 0) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Cannot delete: " + used + (used == 1 ? " event uses" : " events use")
							+ " this category. Rename it or change those events first.");
		}
		categoryRepository.delete(category);
	}

	private Category find(Long id) {
		return categoryRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
	}

	private String cleanColor(String color) {
		if (color == null || color.isBlank()) {
			return null;
		}
		if (!color.matches("#[0-9a-fA-F]{6}")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Colour must look like #2F6FED");
		}
		return color.toUpperCase();
	}

	private String clean(String name) {
		String clean = name == null ? "" : name.trim().replaceAll("\\s+", " ");
		if (clean.isEmpty() || clean.length() > 60) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category name must be 1 to 60 characters");
		}
		return clean;
	}
}
