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
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReedUkService {

    private final ObjectMapper objectMapper;

    @Value("${reed.api-key:NOT_CONFIGURED}")
    private String apiKey;

    @Value("${reed.uk.keywords:java backend}")
    private String keywords;

    @Value("${reed.uk.location:London}")
    private String location;

    @Value("${reed.uk.distance-miles:50}")
    private int distanceMiles;

    @Value("${reed.uk.results:100}")
    private int results;

    private static final String REED_API = "https://www.reed.co.uk/api/1.0/search";
    private static final DateTimeFormatter REED_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public List<Job> fetchJobs() {
        List<Job> jobs = new ArrayList<>();

        if ("NOT_CONFIGURED".equals(apiKey)) {
            log.warn("Reed API key not configured (reed.api-key). Skipping UK ingestion.");
            return jobs;
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(REED_API)
                    .queryParam("keywords", keywords)
                    .queryParam("locationName", location)
                    .queryParam("distancefromLocation", distanceMiles)
                    .queryParam("resultsToTake", results)
                    .build()
                    .encode()
                    .toUriString();

            String credentials = Base64.getEncoder()
                    .encodeToString((apiKey + ":").getBytes());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + credentials)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Reed API returned HTTP {} — {}", response.statusCode(),
                        response.body().substring(0, Math.min(200, response.body().length())));
                return jobs;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode resultList = root.path("results");

            for (JsonNode node : resultList) {
                Job job = parseJob(node);
                if (job != null) jobs.add(job);
            }

            log.info("Fetched {} jobs from Reed UK", jobs.size());

        } catch (Exception e) {
            log.error("Failed to fetch from Reed UK: {}", e.getMessage());
        }

        return jobs;
    }

    private Job parseJob(JsonNode node) {
        try {
            String jobUrl = node.path("jobUrl").asText(null);
            if (jobUrl == null || jobUrl.isBlank()) return null;

            String title = node.path("jobTitle").asText("Unknown Title");
            String company = node.path("employerName").asText("Unknown");
            String locationName = node.path("locationName").asText(location);

            String description = node.path("jobDescription").asText(null);
            if (description != null) {
                description = description.replaceAll("<[^>]+>", "").trim();
                if (description.length() > 2000) description = description.substring(0, 2000);
            }

            LocalDateTime postedDate = null;
            String dateStr = node.path("date").asText(null);
            if (dateStr != null && !dateStr.isBlank()) {
                try {
                    postedDate = java.time.LocalDate.parse(dateStr.trim(), REED_DATE).atStartOfDay();
                } catch (Exception ignored) {}
            }

            return Job.builder()
                    .title(title)
                    .company(company)
                    .location(locationName)
                    .url(jobUrl)
                    .source("reed-uk")
                    .description(description)
                    .skills(SkillExtractor.extract(title, description != null ? description : ""))
                    .domain(DomainClassifier.classify(company, title, description))
                    .postedDate(postedDate)
                    .country("GB")
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse Reed job: {}", e.getMessage());
            return null;
        }
    }
}
