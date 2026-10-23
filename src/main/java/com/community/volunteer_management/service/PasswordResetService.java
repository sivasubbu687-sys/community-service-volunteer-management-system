package com.community.volunteer_management.service;

import com.community.volunteer_management.entity.PasswordResetToken;
import com.community.volunteer_management.entity.User;
import com.community.volunteer_management.repository.PasswordResetTokenRepository;
import com.community.volunteer_management.repository.UserRepository;
import com.community.volunteer_management.security.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@Transactional
public class PasswordResetService {

	private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

	private static final int TOKEN_BYTES = 32;                              // 256 bits of randomness
	private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(30);
	private static final int MAX_REQUESTS_PER_HOUR = 3;                     // per email and per IP address
	private static final Duration RATE_WINDOW = Duration.ofHours(1);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final PasswordResetTokenRepository tokenRepository;
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final EmailService emailService;
	private final RateLimiter rateLimiter;
	private final String baseUrl;

	public PasswordResetService(PasswordResetTokenRepository tokenRepository, UserRepository userRepository,
			PasswordEncoder passwordEncoder, EmailService emailService, RateLimiter rateLimiter,
			@Value("${app.base-url}") String baseUrl) {
		this.tokenRepository = tokenRepository;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.emailService = emailService;
		this.rateLimiter = rateLimiter;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

	// Always behaves the same from the outside, whether or not the email belongs to a user
	public void requestReset(String rawEmail, String clientIp) {
		String email = rawEmail.trim().toLowerCase();

		// Every request counts (even for unknown emails) so the limit reveals nothing about who is registered.
		// "&" (not "&&") so both counters are always updated.
		boolean emailOk = rateLimiter.tryAcquire("reset-email:" + email, MAX_REQUESTS_PER_HOUR, RATE_WINDOW);
		boolean ipOk = rateLimiter.tryAcquire("reset-ip:" + clientIp, MAX_REQUESTS_PER_HOUR, RATE_WINDOW);
		if (!(emailOk & ipOk)) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					"Too many reset requests. Please try again in an hour.");
		}

		userRepository.findByEmail(email).ifPresent(user -> {
			invalidateUnusedTokens(user.getId());   // older links stop working

			byte[] bytes = new byte[TOKEN_BYTES];
			RANDOM.nextBytes(bytes);
			String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

			PasswordResetToken token = new PasswordResetToken();
			token.setUser(user);
			token.setTokenHash(sha256(rawToken));    // only the hash is stored
			token.setExpiresAt(LocalDateTime.now().plus(TOKEN_LIFETIME));
			tokenRepository.save(token);

			String link = baseUrl + "/reset-password.html?token=" + rawToken;
			String name = user.getName();
			String to = user.getEmail();
			// Send only after the token is committed, in the background (errors are just logged)
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					emailService.sendPasswordResetEmail(name, to, link);
				}
			});
		});
	}

	public void resetPassword(String rawToken, String newPassword) {
		PasswordResetToken token = tokenRepository.findByTokenHash(sha256(rawToken.trim()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"This reset link is invalid. Please request a new one."));
		if (token.isUsed()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"This reset link has already been used. Please request a new one.");
		}
		if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"This reset link has expired. Please request a new one.");
		}

		User user = token.getUser();
		user.setPassword(passwordEncoder.encode(newPassword));   // BCrypt
		userRepository.save(user);

		token.setUsed(true);
		tokenRepository.save(token);
		invalidateUnusedTokens(user.getId());                     // no other link for this user may work any more
	}

	// Once a day: remove used and expired tokens
	@Scheduled(cron = "${app.password-reset.cleanup-cron:0 15 3 * * *}")
	public void deleteExpiredAndUsedTokens() {
		int removed = tokenRepository.deleteExpiredAndUsed(LocalDateTime.now());
		log.info("Password reset cleanup: removed {} used/expired token(s)", removed);
	}

	private void invalidateUnusedTokens(Long userId) {
		for (PasswordResetToken old : tokenRepository.findByUserIdAndUsedFalse(userId)) {
			old.setUsed(true);
			tokenRepository.save(old);
		}
	}

	private static String sha256(String value) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is not available", e);
		}
	}
}
