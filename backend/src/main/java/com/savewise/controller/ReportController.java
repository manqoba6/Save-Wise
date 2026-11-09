package com.savewise.controller;

import com.savewise.dto.ReportResponse;
import com.savewise.entity.User;
import com.savewise.exception.ApiException;
import com.savewise.service.CurrentUserService;
import com.savewise.service.ReportExportService;
import com.savewise.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final ReportService reports;
    private final ReportExportService exports;
    private final CurrentUserService currentUser;

    public ReportController(ReportService reports, ReportExportService exports, CurrentUserService currentUser) {
        this.reports = reports;
        this.exports = exports;
        this.currentUser = currentUser;
    }

    /**
     * ?type=DAILY|WEEKLY|MONTHLY|ANNUAL (default MONTHLY) and ?date=2026-10-15, any date inside the period
     * (default today). Weeks run Monday to Sunday.
     */
    @GetMapping
    public ReportResponse get(Authentication auth,
            @RequestParam(defaultValue = "MONTHLY") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reports.generate(currentUser.get(auth), type, date);
    }

    /** Same parameters plus ?format=pdf or csv. Responds with a file download. */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(Authentication auth,
            @RequestParam(defaultValue = "MONTHLY") String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String format) {
        User user = currentUser.get(auth);
        String f = format.trim().toLowerCase(Locale.ROOT);
        if (!f.equals("pdf") && !f.equals("csv")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Format must be pdf or csv.");
        }
        ReportResponse report = reports.generate(user, type, date);

        byte[] body;
        MediaType mediaType;
        if (f.equals("pdf")) {
            body = exports.pdf(report, user.getFirstName() + " " + user.getLastName());
            mediaType = MediaType.APPLICATION_PDF;
        } else {
            body = exports.csv(report);
            mediaType = new MediaType("text", "csv", StandardCharsets.UTF_8);
        }
        String filename = "savewise-" + report.type().toLowerCase(Locale.ROOT) + "-" + report.from() + "." + f;
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
