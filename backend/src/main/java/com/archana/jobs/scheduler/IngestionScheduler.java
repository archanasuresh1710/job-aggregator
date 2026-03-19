package com.archana.jobs.scheduler;

import com.archana.jobs.service.JobIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class IngestionScheduler {

    private final JobIngestionService jobIngestionService;

    // Run every 4 hours
    @Scheduled(fixedRateString = "PT4H")
    public void runIngestion() {
        log.info("Scheduled ingestion triggered.");
        jobIngestionService.ingestAll();
    }
}
