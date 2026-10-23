package com.community.volunteer_management.controller;

import com.community.volunteer_management.security.AuthUser;
import com.community.volunteer_management.service.PdfExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

// "Download PDF" for the volunteer directory and the events list. ADMIN and COORDINATOR only (see SecurityConfig).
@RestController
@RequestMapping("/api")
public class ExportController {

	private final PdfExportService pdfExportService;

	public ExportController(PdfExportService pdfExportService) {
		this.pdfExportService = pdfExportService;
	}

	@GetMapping("/volunteers/export/pdf")
	public ResponseEntity<byte[]> volunteersPdf(@RequestParam(required = false) String search,
			@AuthenticationPrincipal AuthUser user) {
		return pdf(pdfExportService.volunteersPdf(search, user.name()), "volunteers");
	}

	@GetMapping("/events/export/pdf")
	public ResponseEntity<byte[]> eventsPdf(@RequestParam(required = false) String search,
			@RequestParam(required = false) String status, @AuthenticationPrincipal AuthUser user) {
		return pdf(pdfExportService.eventsPdf(search, status, user.name()), "events");
	}

	private ResponseEntity<byte[]> pdf(byte[] bytes, String name) {
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(name + "-" + LocalDate.now() + ".pdf").build().toString())
				.body(bytes);
	}
}
