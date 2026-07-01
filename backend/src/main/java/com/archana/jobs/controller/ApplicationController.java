package com.archana.jobs.controller;

import com.archana.jobs.model.Application;
import com.archana.jobs.repository.ApplicationRepository;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationRepository applicationRepository;

    @GetMapping
    public List<Application> getApplications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String company,
            @RequestParam(required = false, defaultValue = "desc") String sort) {
        return applicationRepository.findByFilters(
                (status  != null && !status.isBlank())  ? status  : null,
                (company != null && !company.isBlank()) ? company : null,
                sort);
    }

    @PostMapping
    public ResponseEntity<Application> addApplication(@RequestBody Application application) {
        application.setId(null); // ensure insert
        return ResponseEntity.ok(applicationRepository.save(application));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Application> updateStatus(@PathVariable Long id,
                                                    @RequestBody java.util.Map<String, String> body) {
        return applicationRepository.findById(id).map(app -> {
            app.setStatus(body.get("status"));
            if (body.containsKey("company")) app.setCompany(body.get("company"));
            if (body.containsKey("appliedDate")) app.setAppliedDate(body.get("appliedDate"));
            if (body.containsKey("remarks")) app.setRemarks(body.get("remarks"));
            if (body.containsKey("interview")) app.setInterview(body.get("interview"));
            if (body.containsKey("resumeLabel")) app.setResumeLabel(body.get("resumeLabel"));
            if (body.containsKey("statusCheckUrl")) app.setStatusCheckUrl(body.get("statusCheckUrl"));
            return ResponseEntity.ok(applicationRepository.save(app));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/bulk-status")
    public ResponseEntity<List<Application>> bulkUpdateStatus(@RequestBody java.util.Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        String status = (String) body.get("status");
        if (ids == null || ids.isEmpty() || status == null) return ResponseEntity.badRequest().build();
        List<Application> updated = ids.stream()
                .map(id -> applicationRepository.findById(id.longValue()))
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .peek(app -> app.setStatus(status))
                .map(applicationRepository::save)
                .toList();
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable Long id) {
        if (!applicationRepository.existsById(id)) return ResponseEntity.notFound().build();
        applicationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadCsv(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty.");
        }
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) return ResponseEntity.badRequest().body("No data in file.");

            // Skip header row
            List<Application> applications = new ArrayList<>();
            for (int i = 1; i < rows.size(); i++) {
                String[] row = rows.get(i);
                if (row.length < 1 || row[0].isBlank()) continue;

                Application app = Application.builder()
                        .company(get(row, 0))
                        .role(get(row, 1))
                        .appliedDate(get(row, 2))
                        .location(get(row, 3))
                        .status(get(row, 4))
                        .interview(get(row, 5))
                        .remarks(get(row, 6))
                        .modeOfApplication(get(row, 7))
                        .resumeLabel(get(row, 8))
                        .build();

                applications.add(app);
            }

            int saved = 0, skipped = 0;
            for (Application app : applications) {
                boolean duplicate = applicationRepository.existsByCompanyIgnoreCaseAndRoleIgnoreCaseAndAppliedDate(
                        app.getCompany(), app.getRole(), app.getAppliedDate());
                if (duplicate) { skipped++; continue; }
                applicationRepository.save(app);
                saved++;
            }

            log.info("CSV import: {} saved, {} skipped (duplicates)", saved, skipped);
            return ResponseEntity.ok("Imported " + saved + " new applications. " +
                    (skipped > 0 ? skipped + " duplicates skipped." : ""));

        } catch (Exception e) {
            log.error("Failed to parse CSV: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Failed to parse CSV: " + e.getMessage());
        }
    }

    private String get(String[] row, int index) {
        if (index >= row.length) return null;
        String val = row[index].trim();
        return val.isBlank() ? null : val;
    }
}
