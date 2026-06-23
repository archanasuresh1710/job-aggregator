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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class GreenhouseService {

    private final ObjectMapper objectMapper;

    @Value("${greenhouse.boards:}")
    private String boardsConfig;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final String BOARD_API =
            "https://boards-api.greenhouse.io/v1/boards/%s/jobs?content=true";

    // Title must contain at least one of these. Greenhouse boards return every role
    // at a company (not just engineering), so this is a hard developer-role gate.
    // We don't include fintech/payments/banking keywords here — they let PM/Analyst
    // titles slip through. Fintech context comes from the curated board list +
    // the DomainClassifier (which uses company name + description).
    private static final List<String> RELEVANT_TITLE_KEYWORDS = List.of(
            "developer", "engineer", "sde", "programmer",
            "backend", "back end", "back-end",
            "java", "spring", "kotlin",
            "full stack", "fullstack",
            "software"
    );

    // Even if a relevant keyword matches, reject if the title contains any of these.
    // Catches managerial / PM / analyst / designer / sales-marketing roles plus
    // adjacent engineering specializations the user isn't targeting (data/ML/security/
    // frontend/QA/SRE/devops/sales engineering).
    private static final List<String> EXCLUDED_TITLE_KEYWORDS = List.of(
            // Non-IC / managerial
            "manager", "director", "head of", "vice president",
            "product owner", "product manager",
            // Adjacent business roles
            "analyst", "designer",
            "recruiter", "talent",
            "sales", "account executive", "account development", "account manager",
            "marketing", "communications",
            "consultant", "intern",
            "support", "success",
            "operations",
            // Adjacent engineering specializations not in scope
            "data engineer", "data scientist",
            "ml engineer", "machine learning engineer", "ai engineer",
            "security engineer", "devops engineer", "infrastructure engineer",
            "qa engineer", "qa automation", "test engineer", "automation engineer",
            "frontend engineer", "front-end engineer", "front end engineer",
            "site reliability", " sre ",
            "sales engineer", "solutions engineer", "customer engineer", "field engineer",
            "hardware engineer", "mechanical engineer", "civil engineer", "electrical engineer"
    );

    private static final List<String> INDIA_LOCATION_KEYWORDS = List.of(
            "india", "bangalore", "bengaluru", "mumbai", "delhi", "gurgaon", "gurugram",
            "hyderabad", "pune", "chennai", "noida", "kolkata", "ahmedabad"
    );

    private static final List<String> REMOTE_LOCATION_KEYWORDS = List.of(
            "remote", "worldwide", "anywhere", "global", "apac", "asia"
    );

    // If location mentions one of these regions exclusively, reject as inaccessible.
    private static final List<String> EXCLUSIVE_NON_INDIA_KEYWORDS = List.of(
            "us only", "usa only", "united states only", "u.s. only",
            "canada only", "uk only", "europe only", "emea only", "latam only",
            "north america only", "australia only", "new zealand only"
    );

    public List<Job> fetchJobs() {
        List<Job> jobs = new ArrayList<>();
        List<String> boards = parseBoards();

        if (boards.isEmpty()) {
            log.warn("No Greenhouse boards configured. Skipping.");
            return jobs;
        }

        for (String board : boards) {
            jobs.addAll(fetchBoard(board));
        }

        log.info("Fetched {} jobs from Greenhouse ({} boards)", jobs.size(), boards.size());
        return jobs;
    }

    private List<String> parseBoards() {
        if (boardsConfig == null || boardsConfig.isBlank()) return List.of();
        return Arrays.stream(boardsConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private List<Job> fetchBoard(String board) {
        List<Job> jobs = new ArrayList<>();
        try {
            String url = String.format(BOARD_API, board);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Greenhouse board '{}' returned HTTP {}", board, response.statusCode());
                return jobs;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode jobList = root.path("jobs");

            int seen = 0;
            for (JsonNode node : jobList) {
                seen++;
                Job job = parseJob(node, board);
                if (job != null) jobs.add(job);
            }

            log.info("Greenhouse [{}]: kept {}/{} jobs", board, jobs.size(), seen);

        } catch (Exception e) {
            log.error("Failed to fetch Greenhouse board '{}': {}", board, e.getMessage());
        }
        return jobs;
    }

    private Job parseJob(JsonNode node, String board) {
        try {
            String url = node.path("absolute_url").asText(null);
            if (url == null || url.isBlank()) return null;

            String title = node.path("title").asText("Unknown Title");
            if (!isRelevant(title)) return null;

            String location = node.path("location").path("name").asText("").trim();
            if (!isAccessibleFromIndia(location)) return null;

            // company_name is set on individual jobs only when the board is a multi-tenant
            // partner board; otherwise it's absent and the board slug is our best label.
            String company = node.path("company_name").asText(null);
            if (company == null || company.isBlank()) company = humanizeBoardSlug(board);

            String description = null;
            String rawContent = node.path("content").asText(null);
            if (rawContent != null && !rawContent.isBlank()) {
                // Greenhouse returns content as HTML-encoded text (entities like &lt;p&gt;).
                // Jsoup decodes entities and strips tags in one pass.
                description = Jsoup.parse(rawContent).text();
                if (description.length() > 5000) description = description.substring(0, 5000);
            }

            LocalDateTime postedDate = parseDate(node.path("updated_at").asText(null));

            String locationLabel = location.isBlank() ? "Remote" : location;

            return Job.builder()
                    .title(title)
                    .company(company)
                    .location(locationLabel)
                    .url(url)
                    .source("greenhouse")
                    .description(description)
                    .skills(SkillExtractor.extract(title, description != null ? description : ""))
                    .domain(DomainClassifier.classify(company, title, description))
                    .postedDate(postedDate)
                    .country("IN")
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse Greenhouse job from board '{}': {}", board, e.getMessage());
            return null;
        }
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            // updated_at is ISO-8601 with offset, e.g. 2026-05-10T14:30:00-04:00
            return OffsetDateTime.parse(raw, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .toLocalDateTime();
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isRelevant(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        if (EXCLUDED_TITLE_KEYWORDS.stream().anyMatch(lower::contains)) return false;
        return RELEVANT_TITLE_KEYWORDS.stream().anyMatch(lower::contains);
    }

    /**
     * Accept jobs that explicitly mention India / an Indian city, or are pure remote
     * with no excluded-region restriction. Reject specific non-India cities.
     */
    private boolean isAccessibleFromIndia(String location) {
        if (location == null || location.isBlank()) return true; // unspecified → likely remote

        String lower = location.toLowerCase(Locale.ROOT);

        if (EXCLUSIVE_NON_INDIA_KEYWORDS.stream().anyMatch(lower::contains)) return false;
        if (INDIA_LOCATION_KEYWORDS.stream().anyMatch(lower::contains)) return true;
        if (REMOTE_LOCATION_KEYWORDS.stream().anyMatch(lower::contains)) return true;

        // Specific city/region not in our India list and not flagged as remote → reject.
        return false;
    }

    private String humanizeBoardSlug(String slug) {
        if (slug == null || slug.isEmpty()) return "Unknown";
        return Character.toUpperCase(slug.charAt(0)) + slug.substring(1);
    }
}
