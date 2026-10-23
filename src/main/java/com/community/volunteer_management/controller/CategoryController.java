package com.community.volunteer_management.controller;

import com.community.volunteer_management.entity.Category;
import com.community.volunteer_management.service.CategoryService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Event categories: everyone signed in can read; only ADMIN can change (see SecurityConfig)
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

	private final CategoryService categoryService;

	public CategoryController(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@GetMapping
	public List<Category> list() {
		return categoryService.findAll();
	}

	@PostMapping
	public Category create(@RequestBody Map<String, String> body) {
		return categoryService.create(body.get("name"), body.get("color"));
	}

	@PutMapping("/{id}")
	public Category update(@PathVariable Long id, @RequestBody Map<String, String> body) {
		return categoryService.update(id, body.get("name"), body.get("color"));
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable Long id) {
		categoryService.delete(id);
	}
}
