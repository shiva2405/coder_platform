package com.coderplatform.controller;

import com.coderplatform.exception.GlobalExceptionHandler;
import com.coderplatform.exception.InvalidSnippetException;
import com.coderplatform.exception.RateLimitExceededException;
import com.coderplatform.exception.SnippetNotFoundException;
import com.coderplatform.model.SnippetResponse;
import com.coderplatform.service.SnippetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SnippetControllerTest {

    @Mock
    private SnippetService snippetService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SnippetController(snippetService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createReturns201() throws Exception {
        when(snippetService.create(any(), eq("203.0.113.10"))).thenReturn(sample("newslug1"));

        mockMvc.perform(post("/api/snippets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-For", "203.0.113.10")
                        .content("{\"language\":\"python\",\"code\":\"print(1)\",\"stdin\":\"\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("newslug1"))
                .andExpect(jsonPath("$.language").value("python"))
                .andExpect(jsonPath("$.code").value("print(1)"));
    }

    @Test
    void getReturnsSnippet() throws Exception {
        when(snippetService.getBySlug("newslug1")).thenReturn(sample("newslug1"));

        mockMvc.perform(get("/api/snippets/newslug1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("newslug1"))
                .andExpect(jsonPath("$.stdin").value("hi"));
    }

    @Test
    void getUnknownSlugReturns404() throws Exception {
        when(snippetService.getBySlug("missing1")).thenThrow(new SnippetNotFoundException("missing1"));

        mockMvc.perform(get("/api/snippets/missing1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Snippet not found"));
    }

    @Test
    void forkReturns201WithoutChangingContract() throws Exception {
        SnippetResponse forked = sample("forked01");
        forked.setForkedFrom("newslug1");
        forked.setCode("print(2)");
        when(snippetService.fork(eq("newslug1"), any(), eq("198.51.100.7"))).thenReturn(forked);

        mockMvc.perform(post("/api/snippets/newslug1/fork")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Real-IP", "198.51.100.7")
                        .content("{\"language\":\"python\",\"code\":\"print(2)\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("forked01"))
                .andExpect(jsonPath("$.forkedFrom").value("newslug1"))
                .andExpect(jsonPath("$.code").value("print(2)"));
    }

    @Test
    void createReturns400ForInvalidSnippet() throws Exception {
        when(snippetService.create(any(), any())).thenThrow(new InvalidSnippetException("Unsupported language: cobol"));

        mockMvc.perform(post("/api/snippets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"cobol\",\"code\":\"DISPLAY 1.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported language: cobol"));
    }

    @Test
    void createReturns429WhenRateLimited() throws Exception {
        when(snippetService.create(any(), any())).thenThrow(new RateLimitExceededException(3600));

        mockMvc.perform(post("/api/snippets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"python\",\"code\":\"print(1)\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "3600"))
                .andExpect(jsonPath("$.status").value(429));
    }

    private static SnippetResponse sample(String slug) {
        SnippetResponse response = new SnippetResponse();
        response.setSlug(slug);
        response.setLanguage("python");
        response.setCode("print(1)");
        response.setStdin("hi");
        response.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        response.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        response.setViewCount(1);
        return response;
    }
}
