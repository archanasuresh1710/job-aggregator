package com.archana.jobs.service;

import com.archana.jobs.model.Job;
import com.archana.jobs.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobIngestionService {

    private final JobRepository jobRepository;
    private final AdzunaService adzunaService;
    private final LinkedInService linkedInService;
    private final RemotiveService remotiveService;
    private final JobMatchingService jobMatchingService;

    public void ingestAll() {
        log.info("Starting job ingestion...");

        List<Job> newlySaved = new ArrayList<>();

        // LinkedIn enriches inline in fetchJobs (parallel, fast). Just dedup + save.
        newlySaved.addAll(saveNew(linkedInService.fetchJobs(), "linkedin"));

        // Adzuna: enrich AFTER dedup so we don't waste 1s/job on duplicates we'd discard.
        newlySaved.addAll(saveAdzuna(adzunaService.fetchJobs()));
        newlySaved.addAll(saveAdzuna(adzunaService.fetchFintechIndia()));

        newlySaved.addAll(saveNew(remotiveService.fetchJobs(), "remotive"));

        log.info("Ingestion complete. {} new jobs saved.", newlySaved.size());

        if (!newlySaved.isEmpty()) {
            int scored = jobMatchingService.scoreJobs(newlySaved);
            log.info("Match scoring: {}/{} new jobs scored.", scored, newlySaved.size());
        }
    }

    /** Plain dedup + save (jobs already enriched, or source has no enrichment). */
    private List<Job> saveNew(List<Job> jobs, String source) {
        List<Job> newOnes = filterNew(jobs);
        List<Job> saved = new ArrayList<>(newOnes.size());
        for (Job job : newOnes) saved.add(jobRepository.save(job));
        log.info("Saved {}/{} new jobs from {}", saved.size(), jobs.size(), source);
        return saved;
    }

    /** Adzuna: dedup → enrich only the new ones → save. */
    private List<Job> saveAdzuna(List<Job> jobs) {
        List<Job> newOnes = filterNew(jobs);
        log.info("Adzuna: {} new of {} fetched — enriching only new ones", newOnes.size(), jobs.size());
        if (!newOnes.isEmpty()) {
            adzunaService.enrichWithFullDescriptions(newOnes);
        }
        List<Job> saved = new ArrayList<>(newOnes.size());
        for (Job job : newOnes) saved.add(jobRepository.save(job));
        log.info("Saved {}/{} new jobs from adzuna", saved.size(), jobs.size());
        return saved;
    }

    private List<Job> filterNew(List<Job> jobs) {
        List<Job> out = new ArrayList<>();
        for (Job job : jobs) {
            if (job.getUrl() == null) continue;
            if (!jobRepository.existsByUrl(job.getUrl())) out.add(job);
        }
        return out;
    }
}
