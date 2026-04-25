package com.archana.jobs.service;

import com.archana.jobs.model.Profile;
import com.archana.jobs.repository.ProfileRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeAnalysisService {

    private static final long PROFILE_ID = 1L;

    private static final String INSTRUCTION = """
            You are a resume analyzer. Given the raw text of a candidate's resume,
            extract structured information so a job-matching system can compare it
            against job descriptions.

            Years of experience rule:
            1. If the profile / summary section at the top of the resume explicitly
               states a years figure (e.g. "5 years experience" or "5+ years"), use
               that number — the candidate's own claim wins over date arithmetic.
            2. Otherwise, sum durations from the work-experience section
               (post-graduation, full-time professional software engineering only)
               and round to the nearest whole year. So 4.7 years → 5, 3.4 → 3.

            For skills, use canonical short names that match how they appear in job
            descriptions: "Java", "Spring Boot", "Kafka", "PostgreSQL", "AWS", "Docker",
            "Kubernetes", "Microservices", "REST", "Payments", "Fintech", etc.
            Do NOT include soft skills, languages, or generic phrases like
            "problem solving" or "team player".

            For seniority, choose one of: "Junior" (0-2 years), "Mid-level" (3-5 years),
            "Senior" (5-10 years), "Lead" / "Staff" (8+ with leadership signals),
            "Principal" (10+ with strong leadership / architecture signals).

            For primaryStack, write a short phrase identifying the candidate's main
            specialization, e.g. "Java/Spring Boot backend" or "Java fintech / payments backend".

            Respond with ONLY a single JSON object — no markdown fences, no preamble,
            no commentary. Schema:
            {
              "skills": [string, ...],
              "primaryStack": string,
              "yearsOfExperience": integer,
              "seniority": "Junior" | "Mid-level" | "Senior" | "Lead" | "Staff" | "Principal",
              "summary": string
            }
            """;

    private final ClaudeCliRunner claude;
    private final ProfileRepository profileRepository;

    public Profile analyzeAndSave(MultipartFile file) throws Exception {
        String text = extractText(file);
        if (text.isBlank()) {
            throw new IllegalArgumentException("Could not extract any text from the uploaded file.");
        }

        log.info("Extracted {} chars from resume '{}'", text.length(), file.getOriginalFilename());

        String prompt = INSTRUCTION + "\n\n--- RESUME TEXT ---\n" + text;
        ParsedResume parsed = claude.runStructured(prompt, ParsedResume.class);

        Profile profile = profileRepository.findById(PROFILE_ID).orElseGet(() -> {
            Profile p = new Profile();
            p.setId(PROFILE_ID);
            return p;
        });

        profile.setResumeFilename(file.getOriginalFilename());
        profile.setResumeUploadedAt(LocalDateTime.now());
        profile.setResumeText(text.length() > 50_000 ? text.substring(0, 50_000) : text);
        profile.setResumeSkills(parsed.skills() == null ? null : String.join(",", parsed.skills()));
        profile.setResumeStack(parsed.primaryStack());
        profile.setResumeYearsOfExperience(parsed.yearsOfExperience());
        profile.setResumeSeniority(parsed.seniority());
        profile.setResumeSummary(parsed.summary());

        return profileRepository.save(profile);
    }

    private String extractText(MultipartFile file) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1); // -1 = no char limit
        Metadata metadata = new Metadata();
        try (InputStream in = file.getInputStream()) {
            parser.parse(in, handler, metadata, new ParseContext());
        }
        return handler.toString().trim();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ParsedResume(
            List<String> skills,
            String primaryStack,
            int yearsOfExperience,
            String seniority,
            String summary
    ) {}
}
