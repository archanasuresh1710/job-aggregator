package com.archana.jobs.service;

import com.archana.jobs.model.Job;
import com.archana.jobs.util.SkillExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NaukriService {

    private static final String NAUKRI_API =
            "https://www.naukri.com/jobapi/v4/search?noOfResults=20" +
            "&urlType=search_by_key_loc&searchType=adv" +
            "&keyword=java+spring+boot&location=bangalore&pageNo=1" +
            "&experienceX=5";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public List<Job> fetchJobs() {
        log.warn("Naukri scraping is disabled — their internal API endpoint is unstable.");
        return new ArrayList<>();
    }

    @SuppressWarnings("unused")
    private List<Job> fetchJobsInternal() {
        List<Job> jobs = new ArrayList<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(NAUKRI_API))
                    .header("appid", "109")
                    .header("systemid", "109")
                    .header("Accept", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://www.naukri.com/")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Naukri API returned HTTP {} — body: {}", response.statusCode(),
                        response.body().substring(0, Math.min(300, response.body().length())));
                return jobs;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode jobList = root.path("jobDetails");

            for (JsonNode node : jobList) {
                Job job = parseNaukriJob(node);
                if (job != null) jobs.add(job);
            }

            log.info("Fetched {} jobs from Naukri", jobs.size());
        } catch (Exception e) {
            log.error("Failed to fetch from Naukri: {}", e.getMessage());
        }
        return jobs;
    }

    private Job parseNaukriJob(JsonNode node) {
        try {
            String url = node.path("jdURL").asText(null);
            if (url == null || url.isBlank()) return null;

            if (!url.startsWith("http")) {
                url = "https://www.naukri.com" + url;
            }

            String title = node.path("title").asText("Unknown Title");
            String company = node.path("companyName").asText("Unknown");
            String location = node.path("placeholders").findPath("label").asText("Bangalore");

            String description = node.path("jobDescription").asText(null);
            if (description != null && description.length() > 2000) {
                description = description.substring(0, 2000);
            }

            // Naukri returns epoch millis in "createdDate"
            LocalDateTime postedDate = null;
            long createdDate = node.path("createdDate").asLong(0);
            if (createdDate > 0) {
                postedDate = LocalDateTime.ofEpochSecond(createdDate / 1000, 0,
                        java.time.ZoneOffset.ofHoursMinutes(5, 30));
            }

            return Job.builder()
                    .title(title)
                    .company(company)
                    .location(location)
                    .url(url)
                    .source("naukri")
                    .description(description)
                    .skills(SkillExtractor.extract(title, description != null ? description : ""))
                    .postedDate(postedDate)
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse Naukri job: {}", e.getMessage());
            return null;
        }
    }
}
