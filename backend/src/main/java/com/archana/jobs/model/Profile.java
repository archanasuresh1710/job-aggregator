package com.archana.jobs.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "profile")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Profile {

    @Id
    private Long id;

    @Column(length = 255)
    private String name;

    @Column(length = 255)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(columnDefinition = "TEXT")
    private String linkedinUrl;

    @Column(columnDefinition = "TEXT")
    private String portfolioUrl;

    @Column(columnDefinition = "TEXT")
    private String resumeUrl;

    // ── Resume analysis (uploaded resume parsed by Claude) ──

    @Column(length = 255)
    private String resumeFilename;

    private LocalDateTime resumeUploadedAt;

    @Column(columnDefinition = "TEXT")
    private String resumeText;          // raw text extracted by Tika

    @Column(columnDefinition = "TEXT")
    private String resumeSkills;        // comma-separated, from Claude

    @Column(length = 255)
    private String resumeStack;         // e.g. "Java/Spring Boot/Kafka backend"

    private Integer resumeYearsOfExperience;

    @Column(length = 50)
    private String resumeSeniority;     // e.g. "Senior", "Mid-level", "Lead"

    @Column(columnDefinition = "TEXT")
    private String resumeSummary;       // 1-2 sentence summary
}
