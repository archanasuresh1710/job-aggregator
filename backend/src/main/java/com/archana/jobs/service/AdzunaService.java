package com.archana.jobs.service;

import com.archana.jobs.model.Job;
import com.archana.jobs.util.DomainClassifier;
import com.archana.jobs.util.SkillExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36";

    /** Throttle between detail-page fetches — Adzuna rate-limits per IP. */
    private static final long ENRICH_DELAY_MS = 1000;

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

    /**
     * Adzuna's API only returns a ~300-500 char snippet. The detail page at
     * adzuna.in/details/{id} embeds the full description in a JS object
     * (`window["az_details"] = {...}`). Scrape it and replace the snippet so
     * the matching service has the full requirements section to work with.
     *
     * Public so the ingestion pipeline can call this AFTER deduping against
     * the DB — no point spending 1s/job enriching duplicates we'll discard.
     */
    public void enrichWithFullDescriptions(List<Job> jobs) {
        int upgraded = 0;
        for (Job job : jobs) {
            try {
                if (enrichOne(job)) upgraded++;
                Thread.sleep(ENRICH_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (upgraded > 0) {
            log.info("Adzuna enrichment upgraded {}/{} descriptions to full text", upgraded, jobs.size());
        }
    }

    /** Returns true if we successfully replaced the snippet with the full description. */
    private boolean enrichOne(Job job) {
        String url = job.getUrl();
        if (url == null || !url.contains("adzuna.")) return false;
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(15000)
                    .get();

            for (Element script : doc.select("script")) {
                String code = script.html();
                if (!code.contains("\"az_details\"")) continue;

                int eq = code.indexOf("window[\"az_details\"]");
                if (eq < 0) continue;
                int braceStart = code.indexOf('{', eq);
                int braceEnd = findMatchingBrace(code, braceStart);
                if (braceStart < 0 || braceEnd < 0) continue;

                JsonNode obj = objectMapper.readTree(code.substring(braceStart, braceEnd + 1));
                String fullDesc = obj.path("description").asText(null);
                if (fullDesc == null || fullDesc.isBlank()) return false;

                // Adzuna's description field can contain HTML tags (e.g. <h1>, <b>, <ul>, <br>).
                // Jsoup.parse(text).text() strips tags and decodes entities cleanly.
                fullDesc = Jsoup.parse(fullDesc).text();

                if (fullDesc.length() > 5000) fullDesc = fullDesc.substring(0, 5000);

                job.setDescription(fullDesc);
                job.setSkills(SkillExtractor.extract(job.getTitle(), fullDesc));
                job.setDomain(DomainClassifier.classify(job.getCompany(), job.getTitle(), fullDesc));
                return true;
            }
        } catch (Exception e) {
            log.debug("Adzuna detail-page fetch failed for {}: {}", url, e.getMessage());
        }
        return false;
    }

    /**
     * Find the matching close-brace, respecting JSON string literals.
     * Returns -1 if unbalanced.
     */
    private int findMatchingBrace(String s, int start) {
        if (start < 0 || start >= s.length() || s.charAt(start) != '{') return -1;
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (inString) {
                if (c == '\\') escape = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
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
            if (description != null && !description.isBlank()) {
                description = Jsoup.parse(description).text();
            }

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
