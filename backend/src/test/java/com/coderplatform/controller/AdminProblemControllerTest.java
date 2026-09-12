package com.coderplatform.controller;

import com.coderplatform.config.AdminAuthInterceptor;
import com.coderplatform.config.AdminConfig;
import com.coderplatform.exception.GlobalExceptionHandler;
import com.coderplatform.model.AdminProblemResponse;
import com.coderplatform.model.Difficulty;
import com.coderplatform.service.ProblemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminProblemControllerTest {

    @Mock
    private ProblemService problemService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AdminConfig adminConfig = new AdminConfig();
        adminConfig.setApiKey("secret-key");
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminProblemController(problemService))
                .addInterceptors(new AdminAuthInterceptor(adminConfig))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsMissingAdminKey() throws Exception {
        mockMvc.perform(get("/api/admin/problems/a-plus-b"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    void rejectsWrongAdminKey() throws Exception {
        mockMvc.perform(get("/api/admin/problems/a-plus-b").header("X-Admin-Key", "nope"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsHiddenTestsWhenAuthorized() throws Exception {
        AdminProblemResponse response = new AdminProblemResponse();
        response.setSlug("a-plus-b");
        response.setTitle("A + B");
        response.setDifficulty(Difficulty.EASY);
        when(problemService.getAdminProblem("a-plus-b")).thenReturn(response);

        mockMvc.perform(get("/api/admin/problems/a-plus-b").header("X-Admin-Key", "secret-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("a-plus-b"));
    }

    @Test
    void createRequiresAdminKey() throws Exception {
        AdminProblemResponse created = new AdminProblemResponse();
        created.setSlug("new-problem");
        created.setTitle("New");
        created.setDifficulty(Difficulty.HARD);
        when(problemService.create(any())).thenReturn(created);

        mockMvc.perform(post("/api/admin/problems")
                        .header("X-Admin-Key", "secret-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "slug": "new-problem",
                                  "title": "New",
                                  "description": "A hard problem",
                                  "difficulty": "HARD",
                                  "timeLimitMs": 1000,
                                  "memoryLimitBytes": 67108864
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.slug").value("new-problem"));
    }
}
