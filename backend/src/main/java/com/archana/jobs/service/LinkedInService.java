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

@Slf4j
@Service
public class LinkedInService {

    // f_E=4 → Mid-Senior level (matches 5+ years experience)
    private static final String LINKEDIN_GUEST_API =
            "https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search" +
            "?keywords=Java+Spring+Boot+Fintech&location=Bangalore&f_TPR=r86400&f_E=4&start=0";

    public List<Job> fetchJobs() {
        List<Job> jobs = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(LINKEDIN_GUEST_API)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                               "AppleWebKit/537.36 (KHTML, like Gecko) " +
                               "Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .timeout(15000)
                    .get();

            Elements jobCards = doc.select("li");
            for (Element card : jobCards) {
                Job job = parseJobCard(card);
                if (job != null) jobs.add(job);
            }

            log.info("Fetched {} jobs from LinkedIn", jobs.size());
        } catch (Exception e) {
            log.error("Failed to fetch LinkedIn jobs: {}", e.getMessage());
        }
        return jobs;
    }

    private Job parseJobCard(Element card) {
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
            String location = locationEl != null ? locationEl.text().trim() : "Bangalore";

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
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse LinkedIn job card: {}", e.getMessage());
            return null;
        }
    }
}
