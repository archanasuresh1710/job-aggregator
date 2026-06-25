package com.archana.jobs.repository;

import com.archana.jobs.model.InterviewQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, Long> {
    List<InterviewQuestion> findAllByOrderBySectionAscCreatedAtAsc();

    @Modifying
    @Query("UPDATE InterviewQuestion q SET q.section = :to WHERE q.section = :from")
    void renameSection(@Param("from") String from, @Param("to") String to);
}
