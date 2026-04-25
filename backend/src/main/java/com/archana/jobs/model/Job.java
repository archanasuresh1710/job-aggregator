package com.archana.jobs.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String title;

    @Column(length = 255)
    private String company;

    @Column(length = 255)
    private String location;

    @Column(unique = true)
    private String url;

    @Column(length = 50)
    private String source;

    @Column(length = 50)
    private String domain; // 'fintech' or 'other'

    @Column(length = 10)
    private String country; // 'IN', 'GB', etc.

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String skills; // comma-separated list of detected skills

    private LocalDateTime postedDate;

    @Builder.Default
    private LocalDateTime ingestedAt = LocalDateTime.now();

    @Builder.Default
    private Boolean isSeen = false;

    @Builder.Default
    private Boolean isBookmarked = false;

    // ── Match scoring (computed by Claude against the user's resume) ──

    private Integer matchScore;          // final 0-100, after experience-gating

    private Integer matchSkillScore;     // raw 0-100 from skill overlap

    @Column(columnDefinition = "TEXT")
    private String matchedSkills;        // comma-separated, intersect with resume

    @Column(columnDefinition = "TEXT")
    private String missingSkills;        // comma-separated, in JD but not in resume

    private Integer yearsRequiredMin;    // null = unknown

    private Integer yearsRequiredMax;    // null = unknown / open

    @Column(length = 20)
    private String experienceFit;        // 'match' | 'underqualified' | 'overqualified' | 'unknown'

    private Integer experienceGapYears;  // years short (0 if not underqualified)

    @Column(columnDefinition = "TEXT")
    private String matchRationale;       // 1-line "why this score"

    private LocalDateTime matchComputedAt;
}
