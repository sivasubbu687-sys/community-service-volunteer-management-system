package com.community.volunteer_management.repository;

import com.community.volunteer_management.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	List<PasswordResetToken> findByUserIdAndUsedFalse(Long userId);

	void deleteByUserId(Long userId);

	@Modifying
	@Query("delete from PasswordResetToken t where t.used = true or t.expiresAt < :now")
	int deleteExpiredAndUsed(@Param("now") LocalDateTime now);
}
