package com.archana.jobs.repository;

import com.archana.jobs.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    Optional<Job> findByUrl(String url);

    boolean existsByUrl(String url);

    @Query(value = """
            SELECT * FROM jobs
            WHERE (CAST(?1 AS text) IS NULL
                   OR LOWER(title) LIKE LOWER('%' || ?1 || '%')
                   OR LOWER(company) LIKE LOWER('%' || ?1 || '%'))
            AND (CAST(?2 AS text) IS NULL OR source = ?2)
            AND (CAST(?3 AS text) IS NULL OR domain = ?3)
            AND (?4 = false OR is_seen = false)
            AND (country = 'IN' OR country IS NULL)
            AND (CAST(?5 AS text) IS NULL
                 OR LOWER(location) LIKE LOWER('%' || ?5 || '%')
                 OR (?5 = 'bangalore' AND LOWER(location) LIKE '%bengaluru%'))
            ORDER BY posted_date DESC NULLS LAST, ingested_at DESC
            """, nativeQuery = true)
    List<Job> findByFilters(String keyword, String source, String domain, boolean hideSeen, String location);
}
