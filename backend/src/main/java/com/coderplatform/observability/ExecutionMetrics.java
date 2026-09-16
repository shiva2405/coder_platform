package com.coderplatform.observability;

import com.coderplatform.model.CodeExecutionResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class ExecutionMetrics {

    public static final String EXECUTIONS = "coder.executions";
    public static final String FAILURES = "coder.execution.failures";
    public static final String DURATION = "coder.execution.duration";

    private final MeterRegistry registry;

    public ExecutionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String language, CodeExecutionResponse response) {
        if (response == null) {
            return;
        }
        String lang = language == null || language.isBlank() ? "unknown" : language;
        String status = response.getStatus() == null ? "ERROR" : response.getStatus().name();

        Counter.builder(EXECUTIONS)
                .description("Completed code executions")
                .tag("language", lang)
                .tag("status", status)
                .register(registry)
                .increment();

        Timer.builder(DURATION)
                .description("Code execution wall time")
                .tag("language", lang)
                .tag("status", status)
                .register(registry)
                .record(Math.max(0, response.getExecutionTime()), TimeUnit.MILLISECONDS);

        if (response.getStatus() != CodeExecutionResponse.Status.SUCCESS) {
            Counter.builder(FAILURES)
                    .description("Failed code executions")
                    .tag("language", lang)
                    .tag("status", status)
                    .register(registry)
                    .increment();
        }
    }
}
