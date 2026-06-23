package com.archana.jobs.controller;

import com.archana.jobs.model.Profile;
import com.archana.jobs.repository.ProfileRepository;
import com.archana.jobs.service.JobMatchingService;
import com.archana.jobs.service.ResumeAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:5173")
public class ProfileController {

    private static final Long PROFILE_ID = 1L;

    private final ProfileRepository profileRepository;
    private final ResumeAnalysisService resumeAnalysisService;
    private final JobMatchingService jobMatchingService;

    @GetMapping
    public ResponseEntity<Profile> getProfile() {
        return ResponseEntity.ok(
            profileRepository.findById(PROFILE_ID).orElseGet(() -> {
                Profile empty = new Profile();
                empty.setId(PROFILE_ID);
                return empty;
            })
        );
    }

    @PutMapping
    public ResponseEntity<Profile> saveProfile(@RequestBody Profile profile) {
        profile.setId(PROFILE_ID);
        Profile existing = profileRepository.findById(PROFILE_ID).orElse(null);

        // Snapshot the pre-save resume fields. We can't compare `existing` against
        // `saved` after save() because JPA merges into the same managed entity,
        // so `existing` would reflect the post-save state.
        String prevSkills    = existing == null ? null : existing.getResumeSkills();
        String prevStack     = existing == null ? null : existing.getResumeStack();
        Integer prevYears    = existing == null ? null : existing.getResumeYearsOfExperience();
        String prevSeniority = existing == null ? null : existing.getResumeSeniority();
        String prevSummary   = existing == null ? null : existing.getResumeSummary();

        // Preserve resume fields if the frontend didn't send them in the PUT body
        if (existing != null) {
            if (profile.getRoleDescription() == null) profile.setRoleDescription(existing.getRoleDescription());
            if (profile.getResumeFilename() == null) profile.setResumeFilename(existing.getResumeFilename());
            if (profile.getResumeUploadedAt() == null) profile.setResumeUploadedAt(existing.getResumeUploadedAt());
            if (profile.getResumeText() == null) profile.setResumeText(existing.getResumeText());
            if (profile.getResumeSkills() == null) profile.setResumeSkills(existing.getResumeSkills());
            if (profile.getResumeStack() == null) profile.setResumeStack(existing.getResumeStack());
            if (profile.getResumeYearsOfExperience() == null) profile.setResumeYearsOfExperience(existing.getResumeYearsOfExperience());
            if (profile.getResumeSeniority() == null) profile.setResumeSeniority(existing.getResumeSeniority());
            if (profile.getResumeSummary() == null) profile.setResumeSummary(existing.getResumeSummary());
            if (profile.getResumeLabels() == null) profile.setResumeLabels(existing.getResumeLabels());
        }
        Profile saved = profileRepository.save(profile);

        boolean changed = existing != null && (
                !Objects.equals(prevSkills,    saved.getResumeSkills())
             || !Objects.equals(prevStack,     saved.getResumeStack())
             || !Objects.equals(prevYears,     saved.getResumeYearsOfExperience())
             || !Objects.equals(prevSeniority, saved.getResumeSeniority())
             || !Objects.equals(prevSummary,   saved.getResumeSummary())
        );

        if (changed) {
            log.info("Resume fields changed via edit — triggering async re-score of all jobs.");
            jobMatchingService.rescoreAllAsync();
        } else {
            log.info("Resume fields unchanged — skipping re-score.");
        }

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/resume")
    public ResponseEntity<?> uploadResume(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Resume file is empty.");
        }
        try {
            Profile saved = resumeAnalysisService.analyzeAndSave(file);
            log.info("New resume parsed — triggering async re-score of all jobs.");
            jobMatchingService.rescoreAllAsync();
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            log.error("Resume analysis failed", e);
            return ResponseEntity.internalServerError().body("Resume analysis failed: " + e.getMessage());
        }
    }

}
