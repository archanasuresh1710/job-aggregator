package com.archana.jobs.service;

import com.archana.jobs.model.Job;
import com.archana.jobs.model.Profile;
import com.archana.jobs.repository.JobRepository;
import com.archana.jobs.repository.ProfileRepository;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobMatchingService {

    private static final long PROFILE_ID = 1L;
    private static final int BATCH_SIZE = 5;
    private static final String MODEL = "haiku";

    private final ClaudeCliRunner claude;
    private final JobRepository jobRepository;
    private final ProfileRepository profileRepository;

    /**
     * Score a list of jobs against the current resume. Saves match fields
     * back to each Job. Skips silently if no resume is uploaded.
     */
    public int scoreJobs(List<Job> jobs) {
        if (jobs.isEmpty()) return 0;

        Profile profile = profileRepository.findById(PROFILE_ID).orElse(null);
        if (profile == null || profile.getResumeSkills() == null) {
            log.info("No resume on file — skipping match scoring for {} jobs.", jobs.size());
            return 0;
        }

        int totalBatches = (jobs.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        log.info("Starting match scoring: {} jobs in {} batches of up to {} (each batch ~30-90s via CLI)...",
                jobs.size(), totalBatches, BATCH_SIZE);

        int totalScored = 0;
        for (int i = 0; i < jobs.size(); i += BATCH_SIZE) {
            List<Job> batch = jobs.subList(i, Math.min(i + BATCH_SIZE, jobs.size()));
            try {
                scoreBatch(batch, profile);
                totalScored += batch.size();
                log.info("Scored batch {}/{} ({} jobs)",
                        (i / BATCH_SIZE) + 1,
                        (jobs.size() + BATCH_SIZE - 1) / BATCH_SIZE,
                        batch.size());
            } catch (Exception e) {
                log.error("Batch scoring failed for jobs {}-{}: {}",
                        i, i + batch.size() - 1, e.getMessage());
                // continue to next batch
            }
        }
        return totalScored;
    }

    @Async
    public void scoreJobsAsync(List<Job> jobs) {
        scoreJobs(jobs);
    }

    /**
     * Score the jobs that actually matter for the feed: unscored, unseen, and
     * India-based. Already-scored jobs are left alone (their scores will be
     * stale relative to a freshly-edited resume — call out a manual rescore
     * endpoint if you ever want to refresh them).
     */
    @Async
    public void rescoreAllAsync() {
        List<Job> candidates = jobRepository.findScoringCandidates();
        log.info("Found {} unscored / unseen jobs to score against the current resume.",
                candidates.size());
        if (candidates.isEmpty()) return;
        int scored = scoreJobs(candidates);
        log.info("Scoring complete: {}/{} jobs scored.", scored, candidates.size());
    }

    private void scoreBatch(List<Job> batch, Profile profile) {
        String prompt = buildPrompt(batch, profile);
        BatchResponse response = claude.runStructured(prompt, BatchResponse.class, MODEL);

        if (response.scores() == null || response.scores().isEmpty()) {
            throw new RuntimeException("Claude returned no scores for batch of " + batch.size());
        }

        // Map back to jobs by jobId
        Map<Long, JobScoreDto> byId = new HashMap<>();
        for (JobScoreDto s : response.scores()) {
            byId.put(s.jobId(), s);
        }

        LocalDateTime now = LocalDateTime.now();
        Integer userYears = profile.getResumeYearsOfExperience();

        for (Job job : batch) {
            JobScoreDto s = byId.get(job.getId());
            if (s == null) {
                log.warn("Claude omitted score for job {}; skipping.", job.getId());
                continue;
            }
            applyScore(job, s, userYears, now);
        }

        jobRepository.saveAll(batch);
    }

    /**
     * Apply Claude's per-job score to the Job entity, including the
     * deterministic experience-gating rule.
     */
    private void applyScore(Job job, JobScoreDto s, Integer userYears, LocalDateTime now) {
        int skillScore = clamp(s.skillScore(), 0, 100);

        Integer minReq = s.yearsRequiredMin();
        Integer gap = computeGap(minReq, userYears);
        String fit = resolveFit(s.experienceFit(), gap);

        int finalScore = applyExperienceGate(skillScore, fit, gap);

        job.setMatchSkillScore(skillScore);
        job.setMatchScore(finalScore);
        job.setMatchedSkills(joinList(s.matchedSkills()));
        job.setMissingSkills(joinList(s.missingSkills()));
        job.setYearsRequiredMin(minReq);
        job.setYearsRequiredMax(s.yearsRequiredMax());
        job.setExperienceFit(fit);
        job.setExperienceGapYears(gap == null ? 0 : gap);
        job.setMatchRationale(s.rationale());
        job.setMatchComputedAt(now);
    }

    private Integer computeGap(Integer minReq, Integer userYears) {
        if (minReq == null || userYears == null) return null;
        return Math.max(0, minReq - userYears);
    }

    /** Trust the gap calculation over Claude's `experienceFit` label. */
    private String resolveFit(String claudeFit, Integer gap) {
        if (gap == null) return "unknown";
        if (gap == 0) {
            // Could still be overqualified, but only Claude knows from the JD level
            return "match".equalsIgnoreCase(claudeFit) || claudeFit == null ? "match"
                    : "overqualified".equalsIgnoreCase(claudeFit) ? "overqualified"
                    : "match";
        }
        return "underqualified";
    }

    /**
     * Deterministic gating rule:
     *   gap == 0 (or unknown)        → score = skillScore
     *   gap == 1                     → cap 60  (stretch role, yellow)
     *   gap >= 2                     → cap 25  (hard pass, red)
     */
    private int applyExperienceGate(int skillScore, String fit, Integer gap) {
        if (!"underqualified".equals(fit) || gap == null) return skillScore;
        if (gap >= 2) return Math.min(skillScore, 25);
        if (gap == 1) return Math.min(skillScore, 60);
        return skillScore;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String joinList(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        return String.join(",", list);
    }

    private String buildPrompt(List<Job> batch, Profile profile) {
        StringBuilder sb = new StringBuilder(8192);

        sb.append("You are a job-resume matching analyzer. Score each job against the candidate's profile.\n\n");
        sb.append("CANDIDATE PROFILE:\n");
        if (profile.getResumeYearsOfExperience() != null)
            sb.append("- Years of experience: ").append(profile.getResumeYearsOfExperience()).append("\n");
        if (profile.getResumeSeniority() != null)
            sb.append("- Seniority: ").append(profile.getResumeSeniority()).append("\n");
        if (profile.getResumeStack() != null)
            sb.append("- Primary stack: ").append(profile.getResumeStack()).append("\n");
        if (profile.getResumeSkills() != null)
            sb.append("- Skills: ").append(profile.getResumeSkills()).append("\n");
        if (profile.getResumeSummary() != null)
            sb.append("- Profile summary: ").append(profile.getResumeSummary()).append("\n");

        sb.append("""

                FOR EACH JOB, RETURN AN OBJECT WITH:
                - jobId: integer (echo back from input)
                - yearsRequiredMin: integer (minimum years the JD demands; null if not stated)
                - yearsRequiredMax: integer (max if a range is stated; otherwise null)
                - experienceFit: "match" | "underqualified" | "overqualified" | "unknown"
                - experienceGapYears: integer (years short of min; 0 if matched/over)
                - skillScore: integer 0-100 (0 = no overlap, 100 = perfect tech-stack overlap)
                - matchedSkills: array of skill names appearing in BOTH the resume and the JD
                - missingSkills: array of up to 5 skills mentioned in the JD that the resume lacks
                - rationale: ONE short sentence explaining the score

                RULES:
                - If JD says "5+ years", yearsRequiredMin = 5, yearsRequiredMax = null
                - If JD says "5-7 years", yearsRequiredMin = 5, yearsRequiredMax = 7
                - If JD only says "Senior" with no number, infer: Junior=0, Mid=3, Senior=5, Lead=8, Principal=10
                - If JD doesn't state experience at all, set yearsRequiredMin to null and experienceFit to "unknown"
                - Skill matching is fuzzy: "Spring" matches "Spring Boot", "k8s" matches "Kubernetes"
                - Output a single JSON object: {"scores": [...]}
                - Output ONLY valid JSON. No markdown fences, no commentary.

                JOBS:
                """);

        sb.append("[\n");
        for (int i = 0; i < batch.size(); i++) {
            Job j = batch.get(i);
            sb.append("  {\n");
            sb.append("    \"jobId\": ").append(j.getId()).append(",\n");
            sb.append("    \"title\": ").append(jsonString(j.getTitle())).append(",\n");
            sb.append("    \"company\": ").append(jsonString(j.getCompany())).append(",\n");
            sb.append("    \"description\": ").append(jsonString(truncate(j.getDescription(), 2000))).append(",\n");
            sb.append("    \"existingSkillsTags\": ").append(jsonString(j.getSkills())).append("\n");
            sb.append("  }").append(i < batch.size() - 1 ? "," : "").append("\n");
        }
        sb.append("]\n");

        return sb.toString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }

    /** Minimal JSON-string encoder (escapes quotes, backslashes, newlines, control chars). */
    private static String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder out = new StringBuilder(s.length() + 16);
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BatchResponse(List<JobScoreDto> scores) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record JobScoreDto(
            Long jobId,
            Integer yearsRequiredMin,
            Integer yearsRequiredMax,
            String experienceFit,
            Integer experienceGapYears,
            Integer skillScore,
            List<String> matchedSkills,
            List<String> missingSkills,
            String rationale
    ) {}
}
