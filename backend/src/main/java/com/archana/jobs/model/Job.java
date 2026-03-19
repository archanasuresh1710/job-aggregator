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
}
