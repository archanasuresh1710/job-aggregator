package com.archana.jobs.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "applications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 255)
    private String company;

    @Column(length = 255)
    private String role;

    @Column(length = 100)
    private String appliedDate;

    @Column(length = 255)
    private String location;

    @Column(length = 50)
    private String status; // Applied, Rejected, No Callback, Interview Round

    @Column(length = 100)
    private String interview;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(length = 100)
    private String modeOfApplication;

    @Column(columnDefinition = "TEXT")
    private String statusCheckUrl;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
