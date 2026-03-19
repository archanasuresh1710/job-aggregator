package com.archana.jobs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class JobAggregatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(JobAggregatorApplication.class, args);
    }
}
