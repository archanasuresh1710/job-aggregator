package com.archana.jobs.controller;

import com.archana.jobs.model.InterviewQuestion;
import com.archana.jobs.repository.InterviewQuestionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import java.util.List;

@RestController
@RequestMapping("/api/interview-questions")
@RequiredArgsConstructor
public class InterviewQuestionController {

    private final InterviewQuestionRepository repository;

    @GetMapping
    public List<InterviewQuestion> getAll() {
        return repository.findAllByOrderBySectionAscCreatedAtAsc();
    }

    @PostMapping
    public ResponseEntity<InterviewQuestion> add(@RequestBody InterviewQuestion question) {
        question.setId(null);
        return ResponseEntity.ok(repository.save(question));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InterviewQuestion> update(@PathVariable Long id,
                                                    @RequestBody InterviewQuestion body) {
        return repository.findById(id).map(q -> {
            q.setSection(body.getSection());
            q.setQuestion(body.getQuestion());
            q.setAnswer(body.getAnswer());
            q.setSource(body.getSource());
            return ResponseEntity.ok(repository.save(q));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) return ResponseEntity.notFound().build();
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/rename-section")
    @Transactional
    public ResponseEntity<Void> renameSection(@RequestBody Map<String, String> body) {
        String from = body.get("from");
        String to = body.get("to");
        if (from == null || to == null || to.isBlank()) return ResponseEntity.badRequest().build();
        repository.renameSection(from, to);
        return ResponseEntity.noContent().build();
    }
}
