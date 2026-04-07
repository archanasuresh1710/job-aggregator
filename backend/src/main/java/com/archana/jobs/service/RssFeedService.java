package com.archana.jobs.service;

import com.archana.jobs.model.Job;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class RssFeedService {

    public List<Job> fetchJobs(String feedUrl, String source) {
        List<Job> jobs = new ArrayList<>();
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(feedUrl).openConnection();
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            int status = conn.getResponseCode();
            if (status != 200) {
                log.error("HTTP {} from {} feed: {}", status, source, feedUrl);
                return jobs;
            }

            SyndFeedInput input = new SyndFeedInput();
            input.setAllowDoctypes(true);
            SyndFeed feed = input.build(new XmlReader(conn.getInputStream()));

            for (SyndEntry entry : feed.getEntries()) {
                Job job = parseEntry(entry, source);
                if (job != null) {
                    jobs.add(job);
                }
            }
            log.info("Fetched {} jobs from {}", jobs.size(), source);
        } catch (Exception e) {
            log.error("Failed to fetch RSS feed from {}: {}", source, e.getMessage());
        }
        return jobs;
    }

    private Job parseEntry(SyndEntry entry, String source) {
        try {
            String url = entry.getLink();
            if (url == null || url.isBlank()) return null;

            String title = entry.getTitle() != null ? entry.getTitle().trim() : "Unknown Title";

            String description = null;
            if (entry.getDescription() != null) {
                description = entry.getDescription().getValue()
                        .replaceAll("<[^>]+>", "")
                        .trim();
                if (description.length() > 2000) {
                    description = description.substring(0, 2000);
                }
            }

            LocalDateTime postedDate = null;
            if (entry.getPublishedDate() != null) {
                postedDate = entry.getPublishedDate()
                        .toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime();
            }

            String company = extractCompany(entry);
            String location = "Bangalore";

            return Job.builder()
                    .title(title)
                    .company(company)
                    .location(location)
                    .url(url)
                    .source(source)
                    .description(description)
                    .postedDate(postedDate)
                    .country("IN")
                    .build();

        } catch (Exception e) {
            log.warn("Failed to parse RSS entry: {}", e.getMessage());
            return null;
        }
    }

    private String extractCompany(SyndEntry entry) {
        if (entry.getAuthor() != null && !entry.getAuthor().isBlank()) {
            return entry.getAuthor().trim();
        }
        if (entry.getTitle() != null && entry.getTitle().contains(" - ")) {
            String[] parts = entry.getTitle().split(" - ");
            if (parts.length >= 2) {
                return parts[parts.length - 1].trim();
            }
        }
        return "Unknown";
    }
}
