package com.archana.jobs.service;

import com.archana.jobs.model.Job;
import com.archana.jobs.util.DomainClassifier;
import com.archana.jobs.util.SkillExtractor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class LinkedInService {

    // f_E=4 → Mid-Senior level (matches 5+ years experience)
    // f_TPR=r604800 → posted in the last 7 days (7 × 86400s)
    private static final String LINKEDIN_GUEST_API_FMT =
            "https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search" +
            "?keywords=Java+Spring+Boot+Fintech&location=%s&f_TPR=r604800&f_E=4&start=0";

    private static final String LINKEDIN_JOB_POSTING_URL =
            "https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/120.0.0.0 Safari/537.36";

    // LinkedIn URLs end with "-<id>" where id is a 10-digit number, e.g.
    // /jobs/view/senior-backend-engineer-at-73-strings-4406880004
    private static final Pattern JOB_ID_PATTERN = Pattern.compile("(\\d{8,})(?:/|$)");

    public List<Job> fetchJobs() {
        return fetchForLocation("Bangalore");
    }

    public List<Job> fetchKochi() {
        return fetchForLocation("Kochi");
    }

    private List<Job> fetchForLocation(String location) {
        List<Job> jobs = new ArrayList<>();
        try {
            String url = String.format(LINKEDIN_GUEST_API_FMT, location);
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(15000)
                    .get();

            Elements jobCards = doc.select("li");
            for (Element card : jobCards) {
                Job job = parseJobCard(card, location);
                if (job != null) jobs.add(job);
            }

            enrichWithFullDescriptions(jobs);

            log.info("Fetched {} jobs from LinkedIn ({})", jobs.size(), location);
        } catch (Exception e) {
            log.error("Failed to fetch LinkedIn jobs ({}): {}", location, e.getMessage());
        }
        return jobs;
    }

    private void enrichWithFullDescriptions(List<Job> jobs) {
        if (jobs.isEmpty()) return;
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, jobs.size()));
        try {
            for (Job job : jobs) {
                pool.submit(() -> enrichOne(job));
            }
            pool.shutdown();
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("LinkedIn enrichment timed out; proceeding with partial data");
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }

    private void enrichOne(Job job) {
        String jobId = extractJobId(job.getUrl());
        if (jobId == null) return;
        try {
            Document doc = Jsoup.connect(LINKEDIN_JOB_POSTING_URL + jobId)
                    .userAgent(USER_AGENT)
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(15000)
                    .get();

            Element body = doc.selectFirst(".show-more-less-html__markup");
            if (body == null) return;

            String fullText = body.text().trim();
            if (fullText.isBlank()) return;

            String seniority = parseSeniority(doc);
            String descriptionWithMeta = seniority != null
                    ? "Seniority: " + seniority + "\n\n" + fullText
                    : fullText;

            if (descriptionWithMeta.length() > 5000) {
                descriptionWithMeta = descriptionWithMeta.substring(0, 5000);
            }

            job.setDescription(descriptionWithMeta);
            job.setSkills(SkillExtractor.extract(job.getTitle(), descriptionWithMeta));
            job.setDomain(DomainClassifier.classify(job.getCompany(), job.getTitle(), descriptionWithMeta));
        } catch (Exception e) {
            log.debug("Full description fetch failed for LinkedIn job {}: {}", jobId, e.getMessage());
        }
    }

    private String parseSeniority(Document doc) {
        for (Element item : doc.select(".description__job-criteria-item")) {
            Element header = item.selectFirst(".description__job-criteria-subheader");
            Element value = item.selectFirst(".description__job-criteria-text");
            if (header != null && value != null
                    && header.text().trim().equalsIgnoreCase("Seniority level")) {
                String text = value.text().trim();
                return text.isBlank() ? null : text;
            }
        }
        return null;
    }

    private String extractJobId(String url) {
        if (url == null) return null;
        Matcher m = JOB_ID_PATTERN.matcher(url);
        return m.find() ? m.group(1) : null;
    }

    private Job parseJobCard(Element card, String defaultLocation) {
        try {
            Element linkEl = card.selectFirst("a.base-card__full-link");
            if (linkEl == null) linkEl = card.selectFirst("a[href*='/jobs/view/']");
            if (linkEl == null) return null;

            String url = linkEl.attr("href").trim();
            if (url.contains("?")) url = url.substring(0, url.indexOf("?"));
            if (url.isBlank()) return null;

            Element titleEl = card.selectFirst(".base-search-card__title");
            String title = titleEl != null ? titleEl.text().trim() : "Unknown Title";

            Element companyEl = card.selectFirst(".base-search-card__subtitle");
            String company = companyEl != null ? companyEl.text().trim() : "Unknown";

            Element locationEl = card.selectFirst(".job-search-card__location");
            String location = locationEl != null ? locationEl.text().trim() : defaultLocation;

            // Extract any description snippet available in the card
            Element descEl = card.selectFirst(".job-search-card__snippet, .base-search-card__metadata");
            String description = descEl != null ? descEl.text().trim() : null;

            LocalDateTime postedDate = null;
            Element timeEl = card.selectFirst("time");
            if (timeEl != null) {
                String datetime = timeEl.attr("datetime");
                if (!datetime.isBlank()) {
                    try {
                        postedDate = LocalDateTime.parse(datetime + "T00:00:00",
                                DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    } catch (Exception ignored) {}
                }
            }

            return Job.builder()
                    .title(title)
                    .company(company)
                    .location(location)
                    .url(url)
                    .source("linkedin")
                    .description(description)
                    .skills(SkillExtractor.extract(title, description != null ? description : ""))
                    .domain(DomainClassifier.classify(company, title, description))
                    .postedDate(postedDate)
                    .country("IN")
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse LinkedIn job card: {}", e.getMessage());
            return null;
        }
    }
}
