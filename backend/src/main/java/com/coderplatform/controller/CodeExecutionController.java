package com.coderplatform.controller;

import com.coderplatform.model.CodeExecutionRequest;
import com.coderplatform.model.CodeExecutionResponse;
import com.coderplatform.model.LanguageInfo;
import com.coderplatform.service.CodeExecutionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class CodeExecutionController {

    private static final Logger logger = LoggerFactory.getLogger(CodeExecutionController.class);

    private final CodeExecutionService executionService;

    public CodeExecutionController(CodeExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/execute")
    public ResponseEntity<CodeExecutionResponse> executeCode(@Valid @RequestBody CodeExecutionRequest request) {
        logger.info("Received execution request for language: {}", request.getLanguage());
        logger.debug("Code length: {} characters", request.getCode().length());

        CodeExecutionResponse response = executionService.execute(request);

        logger.info("Execution completed with status: {} in {}ms", 
                   response.getStatus(), response.getExecutionTime());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/languages")
    public ResponseEntity<List<LanguageInfo>> getSupportedLanguages() {
        logger.debug("Fetching supported languages");
        return ResponseEntity.ok(executionService.getSupportedLanguages());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
