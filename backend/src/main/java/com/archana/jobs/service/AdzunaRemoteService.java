package com.archana.jobs.service;

import com.archana.jobs.model.Job;
import com.archana.jobs.util.DomainClassifier;
import com.archana.jobs.util.SkillExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdzunaRemoteService {

    private final ObjectMapper objectMapper;

    @Value("${adzuna.app-id}")
    private String appId;

    @Value("${adzuna.app-key}")
    private String appKey;

    @Value("${adzuna.remote.url}")
    private String baseUrl;

    @Value("${adzuna.remote.query}")
    private String query;

    @Value("${adzuna.remote.results-per-page}")
    private int resultsPerPage;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public List<Job> fetchJobs() {
        List<Job> jobs = new ArrayList<>();

        if ("YOUR_APP_ID".equals(appId)) {
            log.warn("Adzuna credentials not configured. Skipping remote ingestion.");
            return jobs;
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("app_id", appId)
                    .queryParam("app_key", appKey)
                    .queryParam("what", query)
                    .queryParam("results_per_page", resultsPerPage)
                    .queryParam("sort_by", "date")
                    .build()
                    .encode()
                    .toUriString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Adzuna remote API returned HTTP {}", response.statusCode());
                return jobs;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("results");

            for (JsonNode node : results) {
                Job job = parseJob(node);
                if (job != null) jobs.add(job);
            }

            log.info("Fetched {} remote jobs from Adzuna", jobs.size());

        } catch (Exception e) {
            log.error("Failed to fetch from Adzuna remote: {}", e.getMessage());
        }

        return jobs;
    }

    private Job parseJob(JsonNode node) {
        try {
            String jobId = node.path("id").asText(null);
            if (jobId == null || jobId.isBlank()) return null;
            String url = "https://www.adzuna.co.uk/jobs/details/" + jobId;

            String title = node.path("title").asText("Unknown Title");
            String company = node.path("company").path("display_name").asText("Unknown");
            String locationName = node.path("location").path("display_name").asText("Remote");
            String description = node.path("description").asText(null);

            if (description != null && description.length() > 2000) {
                description = description.substring(0, 2000);
            }

            LocalDateTime postedDate = null;
            String created = node.path("created").asText(null);
            if (created != null) {
                postedDate = LocalDateTime.parse(created, DateTimeFormatter.ISO_DATE_TIME);
            }

            return Job.builder()
                    .title(title)
                    .company(company)
                    .location(locationName)
                    .url(url)
                    .source("adzuna-remote")
                    .description(description)
                    .skills(SkillExtractor.extract(title, description != null ? description : ""))
                    .domain(DomainClassifier.classify(company, title, description))
                    .postedDate(postedDate)
                    .country("REMOTE")
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse Adzuna remote job: {}", e.getMessage());
            return null;
        }
    }
}
