package com.coderplatform.controller;

import com.coderplatform.model.JudgeResultResponse;
import com.coderplatform.service.JudgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/submissions")
public class SubmissionController {

    private final JudgeService judgeService;

    public SubmissionController(JudgeService judgeService) {
        this.judgeService = judgeService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<JudgeResultResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(judgeService.getSubmission(id));
    }
}
