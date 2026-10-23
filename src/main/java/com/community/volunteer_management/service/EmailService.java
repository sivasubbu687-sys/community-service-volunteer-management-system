package com.community.volunteer_management.service;

import com.community.volunteer_management.entity.Event;
import com.community.volunteer_management.entity.Volunteer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.format.DateTimeFormatter;

@Service
public class EmailService {

	private static final Logger log = LoggerFactory.getLogger(EmailService.class);
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy");
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm a");

	private final JavaMailSender mailSender;

	@Value("${spring.mail.username:}")
	private String fromAddress;

	public EmailService(JavaMailSender mailSender) {
		this.mailSender = mailSender;
	}

	// Runs in the background, so a slow or failing mail server never delays or breaks an assignment.
	// Problems are only logged.
	@Async
	public void sendAssignmentEmail(Volunteer volunteer, Event event) {
		if (fromAddress == null || fromAddress.isBlank()) {
			log.warn("Assignment email NOT sent to {}: mail is not configured (set MAIL_USERNAME and MAIL_PASSWORD).",
					volunteer.getEmail());
			return;
		}

		StringBuilder text = new StringBuilder();
		text.append("Hello ").append(volunteer.getName()).append(",\n\n");
		text.append("You have been assigned to the following volunteer event:\n\n");
		text.append("Event:     ").append(event.getTitle()).append("\n");
		text.append("Date:      ").append(event.getEventDate().format(DATE_FORMAT)).append("\n");
		text.append("Time:      ").append(event.getStartTime().format(TIME_FORMAT)).append(" - ")
				.append(event.getEndTime().format(TIME_FORMAT)).append("\n");
		text.append("Location:  ").append(event.getLocation()).append("\n");
		if (event.getDescription() != null && !event.getDescription().isBlank()) {
			text.append("Details:   ").append(event.getDescription()).append("\n");
		}
		text.append("\nThank you for volunteering. Your coordinator will confirm your spot soon.\n\n");
		text.append("Community Volunteer Management System");

		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(fromAddress);
			message.setTo(volunteer.getEmail());
			message.setSubject("Volunteer assignment: " + event.getTitle());
			message.setText(text.toString());
			mailSender.send(message);
			log.info("Assignment email sent to {}", volunteer.getEmail());
		} catch (Exception e) {
			log.error("Could not send assignment email to {}: {}", volunteer.getEmail(), e.getMessage());
		}
	}

	// Password reset email (HTML with a plain-text fallback). Runs in the background; problems are only logged.
	// The link contains the secret token, so it is never written to the log.
	@Async
	public void sendPasswordResetEmail(String name, String toAddress, String link) {
		if (fromAddress == null || fromAddress.isBlank()) {
			log.warn("Password reset email NOT sent to {}: mail is not configured (set MAIL_USERNAME and MAIL_PASSWORD).",
					toAddress);
			return;
		}

		String safeName = HtmlUtils.htmlEscape(name);
		String safeLink = HtmlUtils.htmlEscape(link);
		String plain = "Hello " + name + ",\n\n"
				+ "We received a request to reset your password. Open this link to choose a new one:\n\n"
				+ link + "\n\n"
				+ "The link expires in 30 minutes and can be used once.\n\n"
				+ "If you did not ask for this, you can safely ignore this email. Your password will not change.\n\n"
				+ "Community Volunteer Management System";
		String html = "<div style=\"font-family:Segoe UI,Arial,sans-serif;max-width:520px;margin:0 auto;color:#1e293b\">"
				+ "<h2 style=\"color:#0b2545\">Reset your password</h2>"
				+ "<p>Hello " + safeName + ",</p>"
				+ "<p>We received a request to reset your password. Click the button below to choose a new one.</p>"
				+ "<p style=\"margin:28px 0\"><a href=\"" + safeLink + "\" style=\"background:#14b8a6;color:#ffffff;"
				+ "text-decoration:none;padding:12px 22px;border-radius:8px;font-weight:600;display:inline-block\">"
				+ "Reset password</a></p>"
				+ "<p>This link expires in <strong>30 minutes</strong> and can be used only once.</p>"
				+ "<p style=\"font-size:13px;color:#64748b\">If the button does not work, copy this address into your browser:<br>"
				+ safeLink + "</p>"
				+ "<p style=\"font-size:13px;color:#64748b\">If you did not ask for this, you can safely ignore this email. "
				+ "Your password will not change.</p>"
				+ "<p style=\"font-size:13px;color:#64748b\">Community Volunteer Management System</p>"
				+ "</div>";

		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(fromAddress);
			helper.setTo(toAddress);
			helper.setSubject("Reset your password");
			helper.setText(plain, html);
			mailSender.send(message);
			log.info("Password reset email sent to {}", toAddress);
		} catch (Exception e) {
			log.error("Could not send password reset email to {}: {}", toAddress, e.getMessage());
		}
	}
}
