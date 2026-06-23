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
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobMatchingService {

    private static final long PROFILE_ID = 1L;
    private static final int BATCH_SIZE = 1;
    private static final String MODEL = "haiku";

    private static final Pattern JUNIOR_TITLE = Pattern.compile(
            "\\b(?:sde[\\s-]?1|sde[\\s-]?i|swe[\\s-]?1|swe[\\s-]?i|junior|associate\\s+engineer|entry[\\s-]?level|graduate\\s+engineer|software\\s+engineer\\s+(?:i|1))\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MID_TITLE = Pattern.compile(
            "\\b(?:sde[\\s-]?2|sde[\\s-]?ii|swe[\\s-]?2|swe[\\s-]?ii|software\\s+engineer\\s+(?:ii|2))\\b",
            Pattern.CASE_INSENSITIVE);

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
        Integer maxReq = inferMaxFromTitle(job.getTitle(), s.yearsRequiredMax());
        Integer underGap = computeUnderGap(minReq, userYears);
        Integer overGap = computeOverGap(maxReq, userYears);
        String fit = resolveFit(s.experienceFit(), underGap, overGap);

        List<String> critical = s.criticalMissingSkills();
        int criticalCount = critical == null ? 0 : critical.size();

        int finalScore = skillScore;
        finalScore = applyMustHaveGate(finalScore, criticalCount);
        finalScore = applyExperienceGate(finalScore, underGap, overGap);

        job.setMatchSkillScore(skillScore);
        job.setMatchScore(finalScore);
        job.setMatchedSkills(joinList(s.matchedSkills()));
        job.setMissingSkills(joinList(s.missingSkills()));
        job.setCriticalMissingSkills(joinList(critical));
        job.setYearsRequiredMin(minReq);
        job.setYearsRequiredMax(maxReq);
        job.setExperienceFit(fit);
        job.setExperienceGapYears(underGap == null ? 0 : underGap);
        job.setMatchRationale(s.rationale());
        job.setMatchComputedAt(now);
    }

    private Integer computeUnderGap(Integer minReq, Integer userYears) {
        if (minReq == null || userYears == null) return null;
        return Math.max(0, minReq - userYears);
    }

    /** Only meaningful when the JD states an explicit max (e.g. "3-5 years"). */
    private Integer computeOverGap(Integer maxReq, Integer userYears) {
        if (maxReq == null || userYears == null) return null;
        return Math.max(0, userYears - maxReq);
    }

    /**
     * Backstop: if Claude failed to set yearsRequiredMax for a junior/mid role,
     * infer it from the title. Junior titles cap at 2y, mid titles cap at 5y.
     */
    private Integer inferMaxFromTitle(String title, Integer claudeMax) {
        if (claudeMax != null) return claudeMax;
        if (title == null) return null;
        if (JUNIOR_TITLE.matcher(title).find()) return 2;
        if (MID_TITLE.matcher(title).find()) return 5;
        return null;
    }

    /** Trust the gap calculation over Claude's `experienceFit` label. */
    private String resolveFit(String claudeFit, Integer underGap, Integer overGap) {
        if (underGap == null && overGap == null) return "unknown";
        if (underGap != null && underGap > 0) return "underqualified";
        if (overGap != null && overGap > 0) return "overqualified";
        // gap is 0 on both ends — Claude may still have flagged overqualified from level signals
        return "overqualified".equalsIgnoreCase(claudeFit) ? "overqualified" : "match";
    }

    /**
     * Penalize when the JD demands skills the resume lacks.
     *   1 critical missing  → cap 60
     *   2+ critical missing → cap 35
     */
    private int applyMustHaveGate(int score, int criticalMissingCount) {
        if (criticalMissingCount >= 2) return Math.min(score, 35);
        if (criticalMissingCount == 1) return Math.min(score, 60);
        return score;
    }

    /**
     * Deterministic experience gating in both directions:
     *   underGap == 1                → cap 60 (stretch)
     *   underGap >= 2                → cap 25 (hard pass)
     *   overGap  == 2                → cap 70 (mildly senior for the role)
     *   overGap  3-4                 → cap 50 (clearly mis-leveled)
     *   overGap  >= 5                → cap 25 (way overqualified — won't be a real fit)
     */
    private int applyExperienceGate(int score, Integer underGap, Integer overGap) {
        if (underGap != null) {
            if (underGap >= 2) return Math.min(score, 25);
            if (underGap == 1) return Math.min(score, 60);
        }
        if (overGap != null) {
            if (overGap >= 5) return Math.min(score, 25);
            if (overGap >= 3) return Math.min(score, 50);
            if (overGap == 2) return Math.min(score, 70);
        }
        return score;
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
                - criticalMissingSkills: SUBSET of missingSkills that the JD treats as REQUIRED.
                  Look for explicit signals: "must have", "required", "minimum X years of Y",
                  "should have strong experience in Y", "Y is essential", "Y is mandatory",
                  primary tech listed in the title or first paragraph. Do NOT include "nice to
                  have", "preferred", "bonus", or skills only mentioned once in passing. Empty
                  array if none. Max 5.
                - rationale: ONE short sentence explaining the score

                RULES:
                - If JD says "5+ years", yearsRequiredMin = 5, yearsRequiredMax = null
                - If JD says "5-7 years", yearsRequiredMin = 5, yearsRequiredMax = 7
                - If JD only states a level (no numbers), infer BOTH min and max:
                    * Junior / Associate Engineer / SDE1 / SDE I / SWE1 / SWE I / Software Engineer I / Entry-level / Graduate → min=0, max=2
                    * Mid-level / SDE2 / SDE II / SWE2 / SWE II / Software Engineer II → min=3, max=5
                    * Senior / SDE3 / SDE III / Senior Software Engineer → min=5, max=null
                    * Lead / Staff → min=8, max=null
                    * Principal → min=10, max=null
                  ALWAYS set yearsRequiredMax for Junior and Mid roles — these levels have a real ceiling.
                - If JD doesn't state experience or level at all, set yearsRequiredMin to null and experienceFit to "unknown"
                - Skill matching is fuzzy: "Spring" matches "Spring Boot", "k8s" matches "Kubernetes",
                  AWS matches EC2/S3/Lambda, JavaScript matches TypeScript, SQL matches PostgreSQL/MySQL.
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
            sb.append("    \"description\": ").append(jsonString(truncate(j.getDescription(), 4000))).append(",\n");
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
            List<String> criticalMissingSkills,
            String rationale
    ) {}
}
