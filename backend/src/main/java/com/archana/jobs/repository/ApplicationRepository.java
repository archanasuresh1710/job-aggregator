package com.archana.jobs.repository;

import com.archana.jobs.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    @Query(value = """
            SELECT * FROM applications
            WHERE (CAST(?1 AS text) IS NULL OR LOWER(status) = LOWER(?1))
            AND   (CAST(?2 AS text) IS NULL OR LOWER(company) LIKE LOWER('%' || ?2 || '%'))
            ORDER BY
              CASE WHEN ?3 = 'asc'
                   AND applied_date SIMILAR TO '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
                   THEN CAST(applied_date AS date) END ASC  NULLS LAST,
              CASE WHEN ?3 = 'desc'
                   AND applied_date SIMILAR TO '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'
                   THEN CAST(applied_date AS date) END DESC NULLS LAST,
              created_at DESC
            """, nativeQuery = true)
    List<Application> findByFilters(String status, String company, String sort);

    boolean existsByCompanyIgnoreCaseAndRoleIgnoreCaseAndAppliedDate(
            String company, String role, String appliedDate);
}
