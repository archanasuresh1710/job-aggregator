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
public class AdzunaService {

    private final ObjectMapper objectMapper;

    @Value("${adzuna.app-id}")
    private String appId;

    @Value("${adzuna.app-key}")
    private String appKey;

    @Value("${adzuna.url}")
    private String baseUrl;

    @Value("${adzuna.query}")
    private String query;

    @Value("${adzuna.location}")
    private String location;

    @Value("${adzuna.results-per-page}")
    private int resultsPerPage;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final List<String> REQUIRED_TITLE_KEYWORDS = List.of(
            "java", "spring", "backend", "back end", "back-end", "software engineer",
            "software developer", "sde", "sde2", "sde-2", "full stack", "fullstack",
            "payment", "payments", "fintech"
    );

    public List<Job> fetchJobs() {
        return fetch(query, location);
    }

    public List<Job> fetchFintechIndia() {
        return fetch("java fintech", null);
    }

    private List<Job> fetch(String queryParam, String locationParam) {
        List<Job> jobs = new ArrayList<>();

        if ("YOUR_APP_ID".equals(appId)) {
            log.warn("Adzuna API credentials not configured. Skipping.");
            return jobs;
        }

        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("app_id", appId)
                    .queryParam("app_key", appKey)
                    .queryParam("what", queryParam)
                    .queryParam("results_per_page", resultsPerPage)
                    .queryParam("sort_by", "date");

            if (locationParam != null && !locationParam.isBlank()) {
                builder.queryParam("where", locationParam);
            }

            String url = builder.build().encode().toUriString();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Adzuna API returned HTTP {}", response.statusCode());
                return jobs;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("results");

            for (JsonNode node : results) {
                Job job = parseAdzunaJob(node, locationParam);
                if (job != null) jobs.add(job);
            }

            log.info("Fetched {} jobs from Adzuna (query={}, location={})", jobs.size(), queryParam, locationParam);

        } catch (Exception e) {
            log.error("Failed to fetch from Adzuna: {}", e.getMessage());
        }

        return jobs;
    }

    private boolean isRelevant(String title) {
        String lower = title.toLowerCase();
        return REQUIRED_TITLE_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private Job parseAdzunaJob(JsonNode node, String locationParam) {
        try {
            String url = node.path("redirect_url").asText(null);
            if (url == null || url.isBlank()) return null;
            if (url.contains("?")) url = url.substring(0, url.indexOf("?"));

            String title = node.path("title").asText("Unknown Title");
            if (!isRelevant(title)) return null;

            String company = node.path("company").path("display_name").asText("Unknown");
            String locationName = node.path("location").path("display_name").asText(
                    locationParam != null ? locationParam : "India");
            String description = node.path("description").asText(null);

            LocalDateTime postedDate = null;
            String created = node.path("created").asText(null);
            if (created != null) {
                postedDate = LocalDateTime.parse(created, DateTimeFormatter.ISO_DATE_TIME);
            }

            if (description != null && description.length() > 2000) {
                description = description.substring(0, 2000);
            }

            return Job.builder()
                    .title(title)
                    .company(company)
                    .location(locationName)
                    .url(url)
                    .source("adzuna")
                    .description(description)
                    .skills(SkillExtractor.extract(title, description != null ? description : ""))
                    .domain(DomainClassifier.classify(company, title, description))
                    .postedDate(postedDate)
                    .country("IN")
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse Adzuna job: {}", e.getMessage());
            return null;
        }
    }
}
