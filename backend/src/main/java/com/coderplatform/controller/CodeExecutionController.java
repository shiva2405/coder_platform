package com.coderplatform.controller;

import com.coderplatform.auth.CurrentUser;
import com.coderplatform.model.CodeExecutionRequest;
import com.coderplatform.model.CodeExecutionResponse;
import com.coderplatform.model.LanguageInfo;
import com.coderplatform.model.WorkTicketResponse;
import com.coderplatform.service.ClientKey;
import com.coderplatform.service.CodeExecutionService;
import com.coderplatform.service.ExecutionQuotaService;
import com.coderplatform.service.QueuedWorkService;
import com.coderplatform.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class CodeExecutionController {

    private static final Logger logger = LoggerFactory.getLogger(CodeExecutionController.class);

    private final CodeExecutionService executionService;
    private final QueuedWorkService queuedWorkService;
    private final ExecutionQuotaService quotaService;
    private final CurrentUser currentUser;

    public CodeExecutionController(
            CodeExecutionService executionService,
            QueuedWorkService queuedWorkService,
            ExecutionQuotaService quotaService,
            CurrentUser currentUser
    ) {
        this.executionService = executionService;
        this.queuedWorkService = queuedWorkService;
        this.quotaService = quotaService;
        this.currentUser = currentUser;
    }

    @PostMapping("/execute")
    public ResponseEntity<WorkTicketResponse> executeCode(
            @Valid @RequestBody CodeExecutionRequest request,
            HttpServletRequest httpRequest
    ) {
        String ip = ClientIpResolver.resolve(httpRequest);
        quotaService.consume(currentUser.optional().orElse(null), ip);
        logger.info("Received execution request for language: {}", request.getLanguage());
        logger.debug("Code length: {} characters", request.getCode().length());

        WorkTicketResponse ticket = queuedWorkService.submit(
                request.getLanguage(),
                ClientKey.of(currentUser.optional().orElse(null), ip),
                () -> {
                    CodeExecutionResponse response = executionService.execute(request);
                    logger.info("Execution completed with status: {} in {}ms",
                            response.getStatus(), response.getExecutionTime());
                    return response;
                }
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ticket);
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<WorkTicketResponse> getJob(@PathVariable String id) {
        return ResponseEntity.ok(queuedWorkService.get(id));
    }

    @DeleteMapping("/jobs/{id}")
    public ResponseEntity<WorkTicketResponse> cancelJob(@PathVariable String id) {
        return ResponseEntity.ok(queuedWorkService.cancel(id));
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
