package com.archana.jobs.controller;

import com.archana.jobs.model.Job;
import com.archana.jobs.repository.JobRepository;
import com.archana.jobs.service.JobIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class JobController {

    private final JobRepository jobRepository;
    private final JobIngestionService jobIngestionService;

    @GetMapping
    public List<Job> getJobs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "true") boolean hideSeen,
            @RequestParam(required = false) String country,
            @RequestParam(defaultValue = "false") boolean sponsorship) {
        return jobRepository.findByFilters(keyword, source, domain, hideSeen, country, sponsorship);
    }

    @PatchMapping("/{id}/seen")
    public ResponseEntity<Job> markSeen(@PathVariable Long id) {
        return jobRepository.findById(id).map(job -> {
            job.setIsSeen(true);
            return ResponseEntity.ok(jobRepository.save(job));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/bookmark")
    public ResponseEntity<Job> toggleBookmark(@PathVariable Long id) {
        return jobRepository.findById(id).map(job -> {
            job.setIsBookmarked(!job.getIsBookmarked());
            return ResponseEntity.ok(jobRepository.save(job));
        }).orElse(ResponseEntity.notFound().build());
    }

    // Manual trigger for ingestion (useful during dev)
    @PostMapping("/ingest")
    public ResponseEntity<String> triggerIngestion() {
        jobIngestionService.ingestAll();
        return ResponseEntity.ok("Ingestion triggered.");
    }
}
