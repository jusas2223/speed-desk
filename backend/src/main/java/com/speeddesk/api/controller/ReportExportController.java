package com.speeddesk.api.controller;

import com.speeddesk.api.service.ReportExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportExportController {

    private static final MediaType CSV = new MediaType(
            "text",
            "csv",
            StandardCharsets.UTF_8
    );

    private final ReportExportService reportExportService;

    @GetMapping("/tickets.csv")
    public ResponseEntity<byte[]> tickets() {
        return csv("speed-desk-chamados.csv", reportExportService.ticketsCsv());
    }

    @GetMapping("/assets.csv")
    public ResponseEntity<byte[]> assets() {
        return csv("speed-desk-ativos.csv", reportExportService.assetsCsv());
    }

    @GetMapping("/incidents.csv")
    public ResponseEntity<byte[]> incidents() {
        return csv("speed-desk-incidentes.csv", reportExportService.incidentsCsv());
    }

    private ResponseEntity<byte[]> csv(String filename, byte[] content) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(content);
    }
}
