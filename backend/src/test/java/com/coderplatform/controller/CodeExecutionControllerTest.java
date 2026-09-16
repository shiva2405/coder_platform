package com.coderplatform.controller;

import com.coderplatform.auth.CurrentUser;
import com.coderplatform.exception.GlobalExceptionHandler;
import com.coderplatform.model.CodeExecutionResponse;
import com.coderplatform.model.LanguageInfo;
import com.coderplatform.model.WorkState;
import com.coderplatform.model.WorkTicketResponse;
import com.coderplatform.observability.ExecutionEnvironmentHealthIndicator;
import com.coderplatform.observability.RequestIdFilter;
import com.coderplatform.service.CodeExecutionService;
import com.coderplatform.service.ExecutionQuotaService;
import com.coderplatform.service.QueuedWorkService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CodeExecutionControllerTest {

    @Mock
    private CodeExecutionService executionService;

    @Mock
    private QueuedWorkService queuedWorkService;

    @Mock
    private ExecutionQuotaService quotaService;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private ExecutionEnvironmentHealthIndicator executionHealth;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new CodeExecutionController(
                        executionService,
                        queuedWorkService,
                        quotaService,
                        currentUser,
                        executionHealth
                ))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void missingLanguageReturnsUseful400() throws Exception {
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"\",\"code\":\"print(1)\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value(containsString("Language is required")));
    }

    @Test
    void missingCodeReturnsUseful400() throws Exception {
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"python\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value(containsString("Code is required")));
    }

    @Test
    void emptyBodyReturnsUseful400() throws Exception {
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error", containsString("Language is required")))
                .andExpect(jsonPath("$.error", containsString("Code is required")));
    }

    @Test
    void malformedJsonReturnsUseful400() throws Exception {
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Invalid request body"));
    }

    @Test
    void pathTraversalIsRejectedBeforeQueueing() throws Exception {
        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"language":"python","files":[{"path":"../secret.py","content":"print(1)"}],"entrypoint":"../secret.py"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("..")));
    }

    @Test
    void executeAcceptsValidRequestAndReturnsTicket() throws Exception {
        doNothing().when(quotaService).consume(any(), any());
        WorkTicketResponse ticket = new WorkTicketResponse();
        ticket.setId("job-1");
        ticket.setState(WorkState.QUEUED);
        ticket.setPosition(1);
        ticket.setEstimatedWaitMs(100L);
        when(queuedWorkService.submit(any(), any(), any())).thenReturn(ticket);

        mockMvc.perform(post("/api/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Request-Id", "req-123")
                        .content("{\"language\":\"python\",\"code\":\"print(1)\",\"stdin\":\"\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Request-Id", "req-123"))
                .andExpect(jsonPath("$.id").value("job-1"))
                .andExpect(jsonPath("$.state").value("QUEUED"));
    }

    @Test
    void languagesEndpointReturnsCatalog() throws Exception {
        when(executionService.getSupportedLanguages()).thenReturn(List.of(
                new LanguageInfo("python", "Python", ".py", "print(1)")
        ));

        mockMvc.perform(get("/api/languages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("python"))
                .andExpect(jsonPath("$[0].name").value("Python"));
    }

    @Test
    void healthReturnsUpWhenExecutionEnvironmentWorks() throws Exception {
        when(executionHealth.health()).thenReturn(Health.up()
                .withDetail("runtime", "bash")
                .withDetail("probe", "coder-ok")
                .build());

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.runtime").value("bash"));
    }

    @Test
    void healthReturns503WhenExecutionEnvironmentIsDown() throws Exception {
        when(executionHealth.health()).thenReturn(Health.down()
                .withDetail("runtime", "unavailable")
                .withDetail("error", "Cannot start execution process")
                .build());

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.error").value("Cannot start execution process"));
    }

    @Test
    void requestIdIsGeneratedWhenClientOmitsIt() throws Exception {
        when(executionHealth.health()).thenReturn(Health.up().build());

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void jobLookupReturnsTicket() throws Exception {
        WorkTicketResponse ticket = new WorkTicketResponse();
        ticket.setId("job-9");
        ticket.setState(WorkState.COMPLETED);
        ticket.setResult(CodeExecutionResponse.success("ok\n", 3));
        when(queuedWorkService.get("job-9")).thenReturn(ticket);

        mockMvc.perform(get("/api/jobs/job-9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("job-9"))
                .andExpect(jsonPath("$.result.status").value("SUCCESS"));
    }
}
