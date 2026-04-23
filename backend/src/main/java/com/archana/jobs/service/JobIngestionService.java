package com.archana.jobs.service;

import com.archana.jobs.model.Job;
import com.archana.jobs.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobIngestionService {

    private final JobRepository jobRepository;
    private final AdzunaService adzunaService;
    private final LinkedInService linkedInService;
    private final ReedUkService reedUkService;
    private final RemotiveService remotiveService;

    public void ingestAll() {
        log.info("Starting job ingestion...");
        AtomicInteger newJobsCount = new AtomicInteger(0);

        newJobsCount.addAndGet(saveNew(linkedInService.fetchJobs(), "linkedin"));
        newJobsCount.addAndGet(saveNew(adzunaService.fetchJobs(), "adzuna"));
        newJobsCount.addAndGet(saveNew(adzunaService.fetchFintechIndia(), "adzuna"));
        newJobsCount.addAndGet(saveNew(reedUkService.fetchJobs(), "reed-uk"));
        newJobsCount.addAndGet(saveNew(remotiveService.fetchJobs(), "remotive"));

        log.info("Ingestion complete. {} new jobs saved.", newJobsCount.get());
    }

    private int saveNew(List<Job> jobs, String source) {
        int saved = 0;
        for (Job job : jobs) {
            if (job.getUrl() == null) continue;
            if (!jobRepository.existsByUrl(job.getUrl())) {
                jobRepository.save(job);
                saved++;
            }
        }
        log.info("Saved {}/{} new jobs from {}", saved, jobs.size(), source);
        return saved;
    }
}
