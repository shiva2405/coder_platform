package com.coderplatform.exception;

import com.coderplatform.model.ApiErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SnippetNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(SnippetNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("Snippet not found", HttpStatus.NOT_FOUND.value()));
    }

    @ExceptionHandler(ProblemNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProblemNotFound(ProblemNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("Problem not found", HttpStatus.NOT_FOUND.value()));
    }

    @ExceptionHandler(SubmissionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleSubmissionNotFound(SubmissionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("Submission not found", HttpStatus.NOT_FOUND.value()));
    }

    @ExceptionHandler(TestCaseNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTestCaseNotFound(TestCaseNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse("Test case not found", HttpStatus.NOT_FOUND.value()));
    }

    @ExceptionHandler(InvalidProblemException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidProblem(InvalidProblemException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(UnauthorizedAdminException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(UnauthorizedAdminException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiErrorResponse("Unauthorized", HttpStatus.UNAUTHORIZED.value()));
    }

    @ExceptionHandler(InvalidSnippetException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalid(InvalidSnippetException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(new ApiErrorResponse(ex.getMessage(), HttpStatus.TOO_MANY_REQUESTS.value()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        if (message.isBlank()) {
            message = "Invalid request";
        }
        return ResponseEntity.badRequest()
                .body(new ApiErrorResponse(message, HttpStatus.BAD_REQUEST.value()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        logger.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiErrorResponse("Internal server error", HttpStatus.INTERNAL_SERVER_ERROR.value()));
    }

    private String formatFieldError(FieldError error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : error.getField() + " is invalid";
    }
}
