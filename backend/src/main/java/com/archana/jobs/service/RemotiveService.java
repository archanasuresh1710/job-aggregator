package com.archana.jobs.service;

import com.archana.jobs.model.Job;
import com.archana.jobs.util.DomainClassifier;
import com.archana.jobs.util.SkillExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
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
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemotiveService {

    private final ObjectMapper objectMapper;

    @Value("${remotive.search:java backend}")
    private String search;

    @Value("${remotive.limit:100}")
    private int limit;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Regions that explicitly exclude India
    private static final Set<String> EXCLUDED_REGIONS = Set.of(
            "usa only", "us only", "united states only", "canada only", "uk only",
            "europe only", "european union", "eu only", "latam", "latin america",
            "north america only", "australia only", "new zealand only"
    );

    // Regions that include India
    private static final Set<String> INCLUDED_REGIONS = Set.of(
            "worldwide", "anywhere", "global", "world", "all", "remote",
            "india", "asia", "south asia", "apac", "asia pacific"
    );

    private static final List<String> RELEVANT_TITLE_KEYWORDS = List.of(
            "java", "spring", "backend", "back end", "back-end", "software engineer",
            "software developer", "sde", "full stack", "fullstack", "payment", "fintech"
    );

    public List<Job> fetchJobs() {
        List<Job> jobs = new ArrayList<>();
        try {
            String url = UriComponentsBuilder
                    .fromHttpUrl("https://remotive.com/api/remote-jobs")
                    .queryParam("category", "software-dev")
                    .queryParam("search", search)
                    .queryParam("limit", limit)
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
                log.error("Remotive API returned HTTP {}", response.statusCode());
                return jobs;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode jobList = root.path("jobs");

            for (JsonNode node : jobList) {
                Job job = parseJob(node);
                if (job != null) jobs.add(job);
            }

            log.info("Fetched {} jobs from Remotive", jobs.size());

        } catch (Exception e) {
            log.error("Failed to fetch from Remotive: {}", e.getMessage());
        }
        return jobs;
    }

    private Job parseJob(JsonNode node) {
        try {
            String jobUrl = node.path("url").asText(null);
            if (jobUrl == null || jobUrl.isBlank()) return null;

            String title = node.path("title").asText("Unknown Title");
            if (!isRelevant(title)) return null;

            String candidateLocation = node.path("candidate_required_location").asText("").trim();
            if (!isAccessibleFromIndia(candidateLocation)) return null;

            String company = node.path("company_name").asText("Unknown");

            String description = null;
            String rawDesc = node.path("description").asText(null);
            if (rawDesc != null) {
                description = Jsoup.parse(rawDesc).text();
                if (description.length() > 2000) description = description.substring(0, 2000);
            }

            LocalDateTime postedDate = null;
            String pubDate = node.path("publication_date").asText(null);
            if (pubDate != null) {
                try {
                    postedDate = LocalDateTime.parse(pubDate, DateTimeFormatter.ISO_DATE_TIME);
                } catch (Exception ignored) {}
            }

            String locationLabel = candidateLocation.isBlank() ? "Remote / Worldwide" : candidateLocation;

            return Job.builder()
                    .title(title)
                    .company(company)
                    .location(locationLabel)
                    .url(jobUrl)
                    .source("remotive")
                    .description(description)
                    .skills(SkillExtractor.extract(title, description != null ? description : ""))
                    .domain(DomainClassifier.classify(company, title, description))
                    .postedDate(postedDate)
                    .country("IN")
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse Remotive job: {}", e.getMessage());
            return null;
        }
    }

    private boolean isAccessibleFromIndia(String candidateLocation) {
        if (candidateLocation == null || candidateLocation.isBlank()) return true;

        String lower = candidateLocation.toLowerCase();

        // Explicitly excluded region → reject
        if (EXCLUDED_REGIONS.stream().anyMatch(lower::contains)) return false;

        // Explicitly inclusive region → accept
        if (INCLUDED_REGIONS.stream().anyMatch(lower::contains)) return true;

        // Unknown region string → reject conservatively
        return false;
    }

    private boolean isRelevant(String title) {
        String lower = title.toLowerCase();
        return RELEVANT_TITLE_KEYWORDS.stream().anyMatch(lower::contains);
    }
}
