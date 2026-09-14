package com.coderplatform.controller;

import com.coderplatform.exception.GlobalExceptionHandler;
import com.coderplatform.exception.ProblemNotFoundException;
import com.coderplatform.model.Difficulty;
import com.coderplatform.model.JudgeCaseResponse;
import com.coderplatform.model.JudgeResultResponse;
import com.coderplatform.model.ProblemDetailResponse;
import com.coderplatform.model.ProblemSummaryResponse;
import com.coderplatform.model.SampleCaseResponse;
import com.coderplatform.model.Verdict;
import com.coderplatform.auth.CurrentUser;
import com.coderplatform.model.WorkState;
import com.coderplatform.model.WorkTicketResponse;
import com.coderplatform.service.ExecutionQuotaService;
import com.coderplatform.service.JudgeService;
import com.coderplatform.service.ProblemService;
import com.coderplatform.service.QueuedWorkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProblemControllerTest {

    @Mock
    private ProblemService problemService;

    @Mock
    private JudgeService judgeService;

    @Mock
    private QueuedWorkService queuedWorkService;

    @Mock
    private ExecutionQuotaService quotaService;

    @Mock
    private CurrentUser currentUser;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProblemController(
                        problemService,
                        judgeService,
                        queuedWorkService,
                        quotaService,
                        currentUser
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listReturnsSummariesWithoutHiddenPayload() throws Exception {
        ProblemSummaryResponse summary = new ProblemSummaryResponse();
        summary.setSlug("a-plus-b");
        summary.setTitle("A + B");
        summary.setDifficulty(Difficulty.EASY);
        summary.setTags(List.of("math"));
        summary.setSampleCount(2);
        summary.setTotalTestCases(5);
        when(problemService.listProblems()).thenReturn(List.of(summary));

        mockMvc.perform(get("/api/problems"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slug").value("a-plus-b"))
                .andExpect(jsonPath("$[0].sampleCount").value(2))
                .andExpect(jsonPath("$[0].totalTestCases").value(5))
                .andExpect(jsonPath("$[0].hiddenTests").doesNotExist());
    }

    @Test
    void getReturnsSamplesOnly() throws Exception {
        ProblemDetailResponse detail = new ProblemDetailResponse();
        detail.setSlug("a-plus-b");
        detail.setTitle("A + B");
        detail.setDescription("Add two numbers");
        detail.setDifficulty(Difficulty.EASY);
        SampleCaseResponse sample = new SampleCaseResponse();
        sample.setIndex(1);
        sample.setInput("1 2");
        sample.setExpectedOutput("3");
        sample.setPoints(10);
        detail.setSamples(List.of(sample));
        detail.setHiddenTestCount(3);
        when(problemService.getPublicProblem("a-plus-b")).thenReturn(detail);

        mockMvc.perform(get("/api/problems/a-plus-b"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.samples[0].input").value("1 2"))
                .andExpect(jsonPath("$.hiddenTestCount").value(3))
                .andExpect(jsonPath("$.testCases").doesNotExist());
    }

    @Test
    void unknownProblemReturns404() throws Exception {
        when(problemService.getPublicProblem("missing")).thenThrow(new ProblemNotFoundException("missing"));

        mockMvc.perform(get("/api/problems/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Problem not found"));
    }

    @Test
    void submitHidesHiddenCaseIo() throws Exception {
        JudgeResultResponse result = new JudgeResultResponse();
        result.setId(7L);
        result.setProblemSlug("a-plus-b");
        result.setVerdict(Verdict.WRONG_ANSWER);
        result.setPassedCount(1);
        result.setTotalCount(2);
        result.setCases(List.of(
                JudgeCaseResponse.publicView(1, true, Verdict.ACCEPTED, 2, 10, "1 2", "3", "3", null),
                JudgeCaseResponse.publicView(2, false, Verdict.WRONG_ANSWER, 3, 0, "secret", "99", "0", null)
        ));
        when(currentUser.optional()).thenReturn(java.util.Optional.empty());
        when(queuedWorkService.submit(any(), any(), any())).thenAnswer(invocation -> {
            java.util.concurrent.Callable<?> work = invocation.getArgument(2);
            WorkTicketResponse ticket = new WorkTicketResponse();
            ticket.setId("job-1");
            ticket.setState(WorkState.COMPLETED);
            ticket.setResult(work.call());
            return ticket;
        });
        when(judgeService.submit(eq("a-plus-b"), any())).thenReturn(result);

        mockMvc.perform(post("/api/problems/a-plus-b/submissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"python\",\"code\":\"print(3)\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.result.verdict").value("WRONG_ANSWER"))
                .andExpect(jsonPath("$.result.cases[0].input").value("1 2"))
                .andExpect(jsonPath("$.result.cases[0].expectedOutput").value("3"))
                .andExpect(jsonPath("$.result.cases[0].actualOutput").value("3"))
                .andExpect(jsonPath("$.result.cases[1].input").doesNotExist())
                .andExpect(jsonPath("$.result.cases[1].expectedOutput").doesNotExist())
                .andExpect(jsonPath("$.result.cases[1].actualOutput").doesNotExist());
    }
}
