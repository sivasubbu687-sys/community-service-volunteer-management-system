package com.community.volunteer_management.repository;

import com.community.volunteer_management.entity.Role;
import com.community.volunteer_management.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	boolean existsByVolunteerId(Long volunteerId);

	Optional<User> findByVolunteerId(Long volunteerId);

	long countByRole(Role role);
}
