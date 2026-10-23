package com.community.volunteer_management.service;

import com.community.volunteer_management.entity.AssignmentStatus;
import com.community.volunteer_management.entity.Event;
import com.community.volunteer_management.entity.EventStatus;
import com.community.volunteer_management.entity.Volunteer;
import com.community.volunteer_management.repository.AssignmentRepository;
import com.community.volunteer_management.repository.VolunteerRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Builds the "Download PDF" files for the volunteer directory and the events list.
// The filters are the same ones the screen uses, so the PDF shows exactly what the user was looking at.
@Service
public class PdfExportService {

	private static final Color NAVY = new Color(0x0B, 0x25, 0x45);
	private static final Color TEAL = new Color(0x14, 0xB8, 0xA6);
	private static final Color STRIPE = new Color(0xF1, 0xF5, 0xF9);
	private static final Color LINE = new Color(0xE2, 0xE8, 0xF0);
	private static final Color MUTED = new Color(0x64, 0x74, 0x8B);
	private static final Set<AssignmentStatus> ACTIVE = EnumSet.of(AssignmentStatus.ASSIGNED, AssignmentStatus.CONFIRMED);
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

	private final VolunteerRepository volunteerRepository;
	private final AssignmentRepository assignmentRepository;
	private final EventService eventService;

	public PdfExportService(VolunteerRepository volunteerRepository, AssignmentRepository assignmentRepository,
			EventService eventService) {
		this.volunteerRepository = volunteerRepository;
		this.assignmentRepository = assignmentRepository;
		this.eventService = eventService;
	}

	// ---------- volunteers ----------

	public byte[] volunteersPdf(String search, String generatedBy) {
		String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
		List<Volunteer> volunteers = volunteerRepository.findAll().stream()
				.filter(v -> term.isEmpty() || contains(v.getName(), term) || contains(v.getEmail(), term)
						|| contains(v.getPhone(), term))
				.sorted(Comparator.comparing(Volunteer::getId))
				.toList();

		String subtitle = term.isEmpty() ? "All volunteers" : "Search: \"" + search.trim() + "\"";
		return build("Volunteer Directory", subtitle, generatedBy, false, document -> {
			PdfPTable table = newTable(new float[] { 1f, 3f, 4.5f, 2.5f });
			header(table, "ID", "Name", "Email", "Phone");
			int row = 0;
			for (Volunteer v : volunteers) {
				body(table, row++, String.valueOf(v.getId()), v.getName(), v.getEmail(), v.getPhone());
			}
			document.add(table);
			document.add(total(volunteers.size() + (volunteers.size() == 1 ? " volunteer" : " volunteers")));
		});
	}

	// ---------- events ----------

	public byte[] eventsPdf(String search, String status, String generatedBy) {
		EventStatus wanted = null;
		if (status != null && !status.isBlank()) {
			try {
				wanted = EventStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status: " + status);
			}
		}
		EventStatus statusFilter = wanted;
		String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

		// eventService.findAll() brings the statuses up to date first, so the PDF never shows a stale status
		List<Event> events = eventService.findAll().stream()
				.filter(e -> term.isEmpty() || contains(e.getTitle(), term))
				.filter(e -> statusFilter == null || e.getStatus() == statusFilter)
				.sorted(Comparator.comparing(Event::getEventDate).thenComparing(Event::getStartTime))
				.toList();

		StringBuilder subtitle = new StringBuilder(statusFilter == null ? "All statuses" : label(statusFilter.name()));
		if (!term.isEmpty()) {
			subtitle.append("  |  Title contains \"").append(search.trim()).append("\"");
		}
		return build("Events", subtitle.toString(), generatedBy, true, document -> {
			PdfPTable table = newTable(new float[] { 3.4f, 1.9f, 2.0f, 2.4f, 2.4f, 1.5f, 1.8f });
			header(table, "Title", "Date", "Time", "Location", "Category", "Assigned / Required", "Status");
			int row = 0;
			for (Event e : events) {
				long assigned = assignmentRepository.countByEventIdAndStatusIn(e.getId(), ACTIVE);
				body(table, row++, e.getTitle(), e.getEventDate().toString(),
						e.getStartTime().toString().substring(0, 5) + " - " + e.getEndTime().toString().substring(0, 5),
						e.getLocation(), e.getCategory() == null ? "-" : e.getCategory(),
						assigned + " / " + e.getRequiredVolunteers(), label(e.getStatus().name()));
			}
			document.add(table);
			document.add(total(events.size() + (events.size() == 1 ? " event" : " events")));
		});
	}

	// ---------- shared building blocks ----------

	private interface Content {
		void write(Document document) throws DocumentException;
	}

	private byte[] build(String title, String subtitle, String generatedBy, boolean landscape, Content content) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Document document = new Document(landscape ? PageSize.A4.rotate() : PageSize.A4, 36, 36, 40, 48);
		try {
			PdfWriter writer = PdfWriter.getInstance(document, out);
			writer.setPageEvent(new PageNumbers());
			document.addTitle(title + " - Community Volunteer Management System");
			document.addAuthor("Community Volunteer Management System");
			document.open();

			// logo + title block
			PdfPTable top = new PdfPTable(new float[] { 1f, 8f });
			top.setWidthPercentage(100);
			PdfPCell logoCell = new PdfPCell();
			logoCell.setBorder(Rectangle.NO_BORDER);
			try {
				Image logo = Image.getInstance(new ClassPathResource("static/favicon.png").getContentAsByteArray());
				logo.scaleToFit(46, 46);
				logoCell.addElement(logo);
			} catch (IOException | RuntimeException e) {
				// the PDF is still useful without the logo
			}
			top.addCell(logoCell);

			PdfPCell titleCell = new PdfPCell();
			titleCell.setBorder(Rectangle.NO_BORDER);
			titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
			Paragraph heading = new Paragraph(title, font(20, Font.BOLD, NAVY));
			titleCell.addElement(heading);
			titleCell.addElement(new Paragraph("Community Volunteer Management System", font(9, Font.NORMAL, TEAL)));
			top.addCell(titleCell);
			document.add(top);

			Paragraph meta = new Paragraph(subtitle + "   -   Generated " + LocalDateTime.now().format(STAMP)
					+ (generatedBy == null ? "" : " by " + generatedBy), font(8.5f, Font.NORMAL, MUTED));
			meta.setSpacingBefore(8);
			meta.setSpacingAfter(10);
			document.add(meta);

			content.write(document);
			document.close();
		} catch (DocumentException e) {
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The PDF could not be created");
		}
		return out.toByteArray();
	}

	private PdfPTable newTable(float[] widths) throws DocumentException {
		PdfPTable table = new PdfPTable(widths);
		table.setWidthPercentage(100);
		table.setHeaderRows(1);          // repeated on every page
		table.setSplitLate(false);
		return table;
	}

	private void header(PdfPTable table, String... labels) {
		for (String text : labels) {
			PdfPCell cell = new PdfPCell(new Phrase(text.toUpperCase(Locale.ROOT), font(8, Font.BOLD, Color.WHITE)));
			cell.setBackgroundColor(NAVY);
			cell.setBorder(Rectangle.NO_BORDER);
			cell.setPadding(7);
			table.addCell(cell);
		}
	}

	private void body(PdfPTable table, int row, String... values) {
		for (String value : values) {
			PdfPCell cell = new PdfPCell(new Phrase(value == null || value.isBlank() ? "-" : value, font(9, Font.NORMAL, new Color(0x1E, 0x29, 0x3B))));
			cell.setBackgroundColor(row % 2 == 0 ? Color.WHITE : STRIPE);
			cell.setBorder(Rectangle.BOTTOM);
			cell.setBorderColor(LINE);
			cell.setPadding(6);
			table.addCell(cell);
		}
	}

	private Paragraph total(String text) {
		Paragraph p = new Paragraph("Total: " + text, font(9, Font.BOLD, NAVY));
		p.setSpacingBefore(10);
		return p;
	}

	private Font font(float size, int style, Color color) {
		return FontFactory.getFont(FontFactory.HELVETICA, size, style, color);
	}

	private boolean contains(String value, String term) {
		return value != null && value.toLowerCase(Locale.ROOT).contains(term);
	}

	// UPCOMING -> Upcoming, SOCIAL_WELFARE -> Social Welfare
	private String label(String name) {
		StringBuilder sb = new StringBuilder();
		for (String part : name.split("_")) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(part.charAt(0)).append(part.substring(1).toLowerCase(Locale.ROOT));
		}
		return sb.toString();
	}

	// "Page n" at the bottom of every page
	private static class PageNumbers extends PdfPageEventHelper {
		@Override
		public void onEndPage(PdfWriter writer, Document document) {
			Phrase page = new Phrase("Page " + writer.getPageNumber(), FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, MUTED));
			ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_CENTER, page,
					(document.right() + document.left()) / 2, document.bottom() - 22, 0);
		}
	}
}
